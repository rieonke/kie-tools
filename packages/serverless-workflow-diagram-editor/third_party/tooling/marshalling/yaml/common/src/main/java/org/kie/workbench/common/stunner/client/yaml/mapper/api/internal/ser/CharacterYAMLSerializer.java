/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.workbench.common.stunner.client.yaml.mapper.api.internal.ser;

import org.kie.workbench.common.stunner.client.yaml.mapper.api.YAMLSerializer;
import org.kie.workbench.common.stunner.client.yaml.mapper.api.node.YamlMapping;
import org.kie.workbench.common.stunner.client.yaml.mapper.api.node.YamlSequence;

/**
 * Default {@link AbstractYAMLSerializer} implementation for {@link Character}.
 *
 * @author Nicolas Morel
 * @version $Id: $
 */
public class CharacterYAMLSerializer implements YAMLSerializer<Character> {

  public static final CharacterYAMLSerializer INSTANCE = new CharacterYAMLSerializer();

  /** {@inheritDoc} */
  @Override
  public void serialize(
      YamlMapping writer, String propertyName, Character value, YAMLSerializationContext ctx) {
    writer.addScalarNode(propertyName, value.toString());
  }

  @Override
  public void serialize(YamlSequence writer, Character value, YAMLSerializationContext ctx) {
    if (null == value) {
      if (ctx.isSerializeNulls()) {
        writer.addScalarNode("~");
      }
    } else {
      writer.addScalarNode(value);
    }
  }
}
