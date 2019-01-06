/*
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.core.spec;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import javax.script.ScriptEngineManager;

import org.junit.jupiter.api.Test;

public class JavaScriptEngineSupplierTest {

    @Test
    void testGet() throws Exception {
        JavaScriptEngineSupplier uut = spy(new JavaScriptEngineSupplier());
        uut.get();
        verify(uut).getEngineByName(new String[] { "nashorn", "javascript", "js" });
    }

    @Test
    void testGetJs() throws Exception {
        ScriptEngineManager m = spy(new ScriptEngineManager());
        when(m.getEngineByName("nashorn")).thenReturn(null);
        when(m.getEngineByName("javascript")).thenReturn(null);
        when(m.getEngineByName("js")).thenCallRealMethod();

        JavaScriptEngineSupplier uut = spy(new JavaScriptEngineSupplier(m));
        assertNotNull(uut.get());
    }

    @Test
    void testGetJavascript() throws Exception {
        ScriptEngineManager m = spy(new ScriptEngineManager());
        when(m.getEngineByName("nashorn")).thenReturn(null);
        when(m.getEngineByName("js")).thenReturn(null);
        when(m.getEngineByName("javascript")).thenCallRealMethod();

        JavaScriptEngineSupplier uut = spy(new JavaScriptEngineSupplier(m));
        assertNotNull(uut.get());
    }

    @Test
    void testGetNashorn() throws Exception {
        ScriptEngineManager m = spy(new ScriptEngineManager());
        when(m.getEngineByName("js")).thenReturn(null);
        when(m.getEngineByName("javascript")).thenReturn(null);
        when(m.getEngineByName("nashorn")).thenCallRealMethod();

        JavaScriptEngineSupplier uut = spy(new JavaScriptEngineSupplier(m));
        assertNotNull(uut.get());
    }

    @Test
    void testUnavailable() throws Exception {
        ScriptEngineManager m = spy(new ScriptEngineManager());
        when(m.getEngineByName(anyString())).thenReturn(null);
        JavaScriptEngineSupplier uut = spy(new JavaScriptEngineSupplier(m));
        assertThrows(IllegalStateException.class, () -> {
            uut.get();
        });
    }
}
