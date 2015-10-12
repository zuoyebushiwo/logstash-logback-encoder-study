/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.logstash.logback.composite.accessevent;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;

import ch.qos.logback.access.spi.IAccessEvent;
import net.logstash.logback.composite.AbstractFieldJsonProvider;
import net.logstash.logback.composite.FieldNamesAware;
import net.logstash.logback.composite.JsonWritingUtils;
import net.logstash.logback.fieldnames.LogstashAccessFieldNames;

public class MethodJsonProvider extends AbstractFieldJsonProvider<IAccessEvent> implements FieldNamesAware<LogstashAccessFieldNames> {

    public static final String FIELD_METHOD = "@fields.method";
    
    public MethodJsonProvider() {
        setFieldName(FIELD_METHOD);
    }
    
    @Override
    public void writeTo(JsonGenerator generator, IAccessEvent event) throws IOException {
        JsonWritingUtils.writeStringField(generator, getFieldName(), event.getMethod());
    }
    
    @Override
    public void setFieldNames(LogstashAccessFieldNames fieldNames) {
        setFieldName(fieldNames.getFieldsMethod());
    }

}
