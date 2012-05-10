package org.jboss.errai.jpa.client.local;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;

import org.jboss.errai.jpa.client.local.backend.StorageBackend;
import org.jboss.errai.jpa.client.local.backend.WebStorageBackend;
import org.jboss.errai.marshalling.client.api.MarshallerFramework;

/**
 * The Errai specialization of the JPA 2.0 EntityManager interface. An
 * implementation of this interface, based on all JPA entities visible to the
 * GWT compiler, is generated when the end-user project is compiled.
 *
 * @author Jonathan Fuerth <jfuerth@gmail.com>
 */
public abstract class ErraiEntityManager implements EntityManager {

  // magic incantation. ooga booga!
  static {
    // ensure that the marshalling framework has been initialized
    MarshallerFramework.initializeDefaultSessionProvider();
  }

  /**
   * The metamodel. Gets populated on first call to {@link #getMetamodel()}.
   */
  final ErraiMetamodel metamodel = new ErraiMetamodel();

  /**
   * All persistent instances known to this entity manager.
   */
  final Map<Key<?, ?>, Object> persistenceContext = new HashMap<Key<?, ?>, Object>();

  /**
   * The actual storage backend.
   */
  private final StorageBackend backend = new WebStorageBackend();

  /**
   * Constructor for subclasses.
   */
  protected ErraiEntityManager() {
  }

  /**
   * Populates the metamodel of this EntityManager. Called by getMetamodel() one
   * time only.
   * <p>
   * The implementation of this method must populate the metamodel with all
   * known entity types, managed types, and so on. The implementation must
   * freeze the metamodel before returning. The first call to
   * {@link #getMetamodel()} throws RuntimeException if
   * {@code this.metamodel.isFrozen() == false} after this method returns.
   * <p>
   * Note that this method is normally implemented by a generated subclass, but
   * handwritten subclasses may also be useful for testing purposes.
   */
  protected abstract void populateMetamodel();

  /**
   * This method performs the unchecked (but safe) cast of
   * {@code object.getClass()} to {@code Class<T>}. Using this method avoids the
   * need to mark larger blocks of code with a SuppressWarnings annotation.
   *
   * @param object
   *          The object to get the associated Class object from. Not null.
   * @return The Class object with type parameter fixed to the compile-time type
   *         of object.
   */
  @SuppressWarnings("unchecked")
  private <T> Class<T> getNarrowedClass(T object) {
    return (Class<T>) object.getClass();
  }

  /**
   * Performs an unchecked cast of the given value to the given type. This
   * inlineable method exists because GWT does not implement Class.cast(), which
   * necessitates unchecked casts in generic code. It's important to narrow the
   * unchecked warning suppression to the smallest possible amount of code.
   *
   * @param type The type that {@code value} is believed to have.
   * @param value The value to cast.
   * @return value
   */
  @SuppressWarnings("unchecked")
  private static final <T> T cast(Class<T> type, Object value) {
    return (T) value;
  }

  /**
   * Generates a new ID value for the given entity instance that is guaranteed
   * to be unique <i>on this client</i>. If the entity instance with this ID is
   * ever synchronized to the server, this client-local ID will be replaced by a
   * permanent server-generated ID.
   * <p>
   * This method only works for attributes that are configured as
   * {@code @GeneratedValue}s. The GenerationType has no effect locally, but of
   * course it will come into play on the server side when and if the entity is
   * synchronized to the server.
   *
   * @param entityInstance
   *          the entity instance to receive the generated ID. This attribute of
   *          that entity instance will be set to the newly generated ID value.
   * @return the generated ID value, which has already been set on the entity
   *         instance.
   */
  public <X, T> T generateAndSetLocalId(X entityInstance, ErraiSingularAttribute<X, T> attr) {
    T nextId = attr.getValueGenerator().next();
    attr.set(entityInstance, nextId);
    return nextId;
  }

  private <X, T> Key<X, T> lookupManagedEntity(X entity, boolean throwIfAbsent) {
    // this implementation becomes poor when the persistence context is large.
    // Turning the persistenceContext into a bimap where the value set is done by object identity would be better.
    // in fact, for the generics to behave themselves, it's probably best to create a whole PersistenceContext class.
    for (Entry<Key<?, ?>, Object> entry : persistenceContext.entrySet()) {
      if (entry.getValue() == entity) {
        return (Key<X, T>) entry.getKey();
      }
    }

    if (throwIfAbsent) {
      throw new IllegalArgumentException("Not a managed entity: " + entity);
    }
    return null;
  }

  /**
   * As they say in television, "this is where the magic happens." This method
   * attempts to resolve the given object as an entity and put that entity into
   * the given state, taking into account its existing state and performing the
   * required side effects during the state transition.
   */
  private <T> void changeEntityState(T entity, EntityState newState) {
    ErraiEntityType<T> entityType = getMetamodel().entity(getNarrowedClass(entity));

    ErraiSingularAttribute<? super T, ?> idAttr;
    switch (entityType.getIdType().getPersistenceType()) {
    case BASIC:
      idAttr = entityType.getId(entityType.getIdType().getJavaType());
      break;
    default:
      throw new RuntimeException(entityType.getIdType().getPersistenceType() + " ids are not yet supported");
    }
    Object id = idAttr.get(entity);
    if (id == null) {
      id = generateAndSetLocalId(entity, idAttr);
      // TODO track this generated ID for later reconciliation with the server
    }

    Key<T, ?> key = new Key<T, Object>(entityType, id);

    final EntityState oldState;
    if (persistenceContext.get(key) != null) {
      oldState = EntityState.MANAGED;
    }
    else if (backend.get(key) != null) {
      oldState = EntityState.DETACHED;
    }
    else {
      oldState = EntityState.NEW;
    }
    // TODO handle REMOVED state

    switch (newState) {
    case MANAGED:
      switch (oldState) {
      case NEW:
      case REMOVED:
        entityType.deliverPrePersist(entity);
        persistenceContext.put(key, entity);
        backend.put(key, entity);
        entityType.deliverPostPersist(entity);
        // FALLTHROUGH
      case MANAGED:
        // no-op, but cascade to relatives
        break;
      case DETACHED:
        throw new EntityExistsException();
      }
      break;
    case DETACHED:
      switch (oldState) {
      case NEW:
      case DETACHED:
        // ignore
        break;
      case MANAGED:
      case REMOVED:
        persistenceContext.remove(key);
        break;
      }
      break;
    case REMOVED:
      switch (oldState) {
      case NEW:
      case MANAGED:
        entityType.deliverPreRemove(entity);
        persistenceContext.remove(key);
        backend.remove(key);
        entityType.deliverPostRemove(entity);
        break;
      case DETACHED:
        throw new IllegalArgumentException("Entities can't transition from " + oldState + " to " + newState);
      case REMOVED:
        // ignore
        break;
      }
      break;
    case NEW:
      throw new IllegalArgumentException("Entities can't transition from " + oldState + " to " + newState);
    }
  }

  /**
   * Updates the persistent representation of the given entity in this entity
   * manager's storage backend.
   * <p>
   * This methods checks if the entity value has truly changed, and if so it
   * fires the PreUpdate and PostUpdate events.
   * <p>
   * This method also verifies that the entity's current identity matches the
   * key's identity. In JPA 2.0, application code is not allowed to modify a
   * managed entity's ID attribute. This is just a safety check to ensure that
   * hasn't happened.
   *
   * @param key
   *          The entity's key in the persistence context.
   * @param entity
   *          The "live" entity value in the persistence context.
   * @throws PersistenceException
   *           if the entity's current ID attribute value differs from the one
   *           in the key (which would have been its identity when it first
   *           became managed).
   */
  private <X> void updateInBackend(Key<X, ?> key, X entity) {
    ErraiEntityType<X> entityType = getMetamodel().entity(getNarrowedClass(entity));
    if (backend.isModified(key, entity)) {
      Object currentId = entityType.getId(Object.class).get(entity);
      if (!key.getId().equals(currentId)) {
        throw new PersistenceException(
                "Detected ID attribute change in managed entity. Expected ID: " +
                key.getId() + "; Actual ID: " + currentId);
      }
      entityType.deliverPreUpdate(entity);
      backend.put(key, entity);
      entityType.deliverPostUpdate(entity);
    }
  }

  // -------------- Actual JPA API below this line -------------------

  @Override
  public ErraiMetamodel getMetamodel() {
    if (!metamodel.isFrozen()) {
      populateMetamodel();
      if (!metamodel.isFrozen()) {
        throw new RuntimeException("The populateMetamodel() method didn't call metamodel.freeze()!");
      }
    }
    return metamodel;
  }

  @Override
  public void persist(Object entity) {
    changeEntityState(entity, EntityState.MANAGED);
  }

  @Override
  public void flush() {
    // deferred backend operations not (yet!) implemented

    // persist updates to entities in the persistence context
    for (Map.Entry<Key<?, ?>, Object> entry : persistenceContext.entrySet()) {
      // type safety warning should go away when we have a real PersistenceContext implementation
      updateInBackend((Key<Object, ?>) entry.getKey(), entry.getValue());
    }
  }

  @Override
  public void detach(Object entity) {
    changeEntityState(entity, EntityState.DETACHED);
  }

  @Override
  public void clear() {
    List<?> entities = new ArrayList<Object>(persistenceContext.values());
    for (Object entity : entities) {
      detach(entity);
    }
  }

  @Override
  public <X> X find(Class<X> entityClass, Object primaryKey) {
    Key<X, ?> key = Key.get(this, entityClass, primaryKey);
    X entity = cast(entityClass, persistenceContext.get(key));
    if (entity == null) {
      entity = backend.get(key);
      if (entity != null) {
        persistenceContext.put(key, entity);

        // XXX when persistenceContext gets its own class, this should go on the ultimate ingress point
        getMetamodel().entity(entityClass).deliverPostLoad(entity);
      }
    }
    return entity;
  }

  @Override
  public <T> T find(Class<T> entityClass, Object primaryKey, Map<String, Object> properties) {
    return find(entityClass, primaryKey);
  }

  @Override
  public void remove(Object entity) {
    changeEntityState(entity, EntityState.REMOVED);
  }
}
