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
package net.logstash.logback.stacktrace;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.boolex.EvaluationException;
import ch.qos.logback.core.boolex.EventEvaluator;

public class ShortenedThrowableConverterTest {
    
    private static class StackTraceElementGenerator {
        public static void generateSingle() {
            oneSingle();
        }
        public static void oneSingle() {
            twoSingle();
        }
        private static void twoSingle() {
            threeSingle();
        }
        private static void threeSingle() {
            four();
        }
        private static void four() {
            five();
        }
        private static void five() {
            six();
        }
        private static void six() {
            seven();
        }
        private static void seven() {
            eight();
        }
        private static void eight() {
            throw new RuntimeException("message");
        }
        public static void generateCausedBy() {
            oneCausedBy();
        }
        private static void oneCausedBy() {
            twoCausedBy();
        }
        private static void twoCausedBy() {
            try {
                threeSingle();
            } catch (RuntimeException e) {
                throw new RuntimeException("wrapper", e);
            }
        }
        public static void generateSuppressed() {
            oneSuppressed();
        }
        private static void oneSuppressed() {
            twoSuppressed();
        }
        private static void twoSuppressed() {
            try {
                threeSingle();
            } catch (RuntimeException e) {
                RuntimeException newException = new RuntimeException();
                newException.addSuppressed(e);
                throw newException;
            }
        }
    }
    
    @Test
    public void testDepthTruncation() {
        
        try {
            StackTraceElementGenerator.generateSingle();
            Assert.fail();
        } catch (RuntimeException e) {
            ShortenedThrowableConverter converter = new ShortenedThrowableConverter();
            
            /*
             * First get the un-truncated length
             */
            converter.setMaxDepthPerThrowable(ShortenedThrowableConverter.FULL_MAX_DEPTH_PER_THROWABLE);
            String formatted = converter.convert(createEvent(e));
            int totalLines = countLines(formatted);
            
            /*
             * Now truncate and compare
             */
            converter.setMaxDepthPerThrowable(totalLines - 5);
            formatted = converter.convert(createEvent(e));
            
            Assert.assertEquals(totalLines - 3, countLines(formatted));
            Assert.assertTrue(formatted.contains("4 frames truncated"));
            
        }
    }
    @Test
    public void testLengthTruncation() {
        
        try {
            StackTraceElementGenerator.generateSingle();
            Assert.fail();
        } catch (RuntimeException e) {
            ShortenedThrowableConverter converter = new ShortenedThrowableConverter();
            
            /*
             * First get the un-truncated length
             */
            converter.setMaxDepthPerThrowable(ShortenedThrowableConverter.FULL_MAX_DEPTH_PER_THROWABLE);
            String formatted = converter.convert(createEvent(e));
            int totalLength = formatted.length();
            
            /*
             * Now truncate and compare
             */
            converter.setMaxLength(totalLength - 10);
            formatted = converter.convert(createEvent(e));
            
            Assert.assertEquals(totalLength - 10, formatted.length());
            Assert.assertTrue(formatted.endsWith("..."));
            
        }
    }
    @Test
    public void testExclusion_consecutive() {
        
        try {
            StackTraceElementGenerator.generateSingle();
            Assert.fail();
        } catch (RuntimeException e) {
            ShortenedThrowableConverter converter = new ShortenedThrowableConverter();
            converter.addExclude("one");
            converter.addExclude("two");
            converter.addExclude("four");
            converter.addExclude("five");
            converter.addExclude("six");
            converter.setMaxDepthPerThrowable(8);
            String formatted = converter.convert(createEvent(e));
            Assert.assertTrue(formatted.contains("2 frames excluded"));
            Assert.assertTrue(formatted.contains("3 frames excluded"));
            Assert.assertEquals(12, countLines(formatted));
        }
    }
    @Test
    public void testExclusion_noConsecutive() {
        
        try {
            StackTraceElementGenerator.generateSingle();
            Assert.fail();
        } catch (RuntimeException e) {
            ShortenedThrowableConverter converter = new ShortenedThrowableConverter();
            converter.setExcludes(Collections.singletonList("one"));
            converter.setMaxDepthPerThrowable(8);
            String formatted = converter.convert(createEvent(e));
            Assert.assertFalse(formatted.contains("frames excluded"));
            Assert.assertEquals(10, countLines(formatted));
        }
    }

    @Test
    public void testExclusion_atEnd() {
        
        try {
            StackTraceElementGenerator.generateSingle();
            Assert.fail();
        } catch (RuntimeException e) {
            ShortenedThrowableConverter converter = new ShortenedThrowableConverter();
            
            /*
             * First get the un-truncated stacktrace
             */
            converter.setMaxDepthPerThrowable(ShortenedThrowableConverter.FULL_MAX_DEPTH_PER_THROWABLE);
            String formatted = converter.convert(createEvent(e));
            
            /*
             * Find the last two frames
             */
            
            List<String> lines = getLines(formatted);
            
            /*
             * Now truncate and compare
             */
            converter.addExclude(extractClassAndMethod(lines.get(lines.size() - 2)) + "$");
            converter.addExclude(extractClassAndMethod(lines.get(lines.size() - 1)) + "$");
            formatted = converter.convert(createEvent(e));
            
            Assert.assertEquals(lines.size() - 1, countLines(formatted));
            Assert.assertTrue(formatted.contains("2 frames excluded"));
            
        }
    }

    @Test
    public void testCausedBy() {
        
        try {
            StackTraceElementGenerator.generateCausedBy();
            Assert.fail();
        } catch (RuntimeException e) {
            ShortenedThrowableConverter converter = new ShortenedThrowableConverter();
            converter.setMaxDepthPerThrowable(8);
            String formatted = converter.convert(createEvent(e));
            Assert.assertTrue(formatted.contains("Caused by"));
            Assert.assertTrue(formatted.contains("common frames omitted"));
            Assert.assertTrue(formatted.indexOf("message") > formatted.indexOf("wrapper"));
        }
    }

    @Test
    public void testRootCauseFirst() {
        
        try {
            StackTraceElementGenerator.generateCausedBy();
            Assert.fail();
        } catch (RuntimeException e) {
            ShortenedThrowableConverter converter = new ShortenedThrowableConverter();
            converter.setRootCauseFirst(true);
            converter.setMaxDepthPerThrowable(8);
            String formatted = converter.convert(createEvent(e));
            Assert.assertTrue(formatted.contains("Wrapped by"));
            Assert.assertTrue(formatted.contains("common frames omitted"));
            Assert.assertTrue(formatted.indexOf("message") < formatted.indexOf("wrapper"));
        }
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testEvaluator() throws EvaluationException {
        
        try {
            StackTraceElementGenerator.generateCausedBy();
            Assert.fail();
        } catch (RuntimeException e) {
            ShortenedThrowableConverter converter = new ShortenedThrowableConverter();
            
            EventEvaluator evaluator = mock(EventEvaluator.class);
            when(evaluator.evaluate(any(Object.class))).thenReturn(true);
            converter.addEvaluator(evaluator);
            String formatted = converter.convert(createEvent(e));
            Assert.assertEquals("", formatted);
        }
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testOptions() throws EvaluationException {
        
        EventEvaluator evaluator = mock(EventEvaluator.class);
        Map<String, EventEvaluator> evaluatorMap = new HashMap<String, EventEvaluator>();
        evaluatorMap.put("evaluator", evaluator);
        
        Context context = mock(Context.class);
        when(context.getObject(CoreConstants.EVALUATOR_MAP)).thenReturn(evaluatorMap);
        
        ShortenedThrowableConverter converter = new ShortenedThrowableConverter();
        converter.setContext(context);
        
        // test full values
        converter.setOptionList(Arrays.asList("full", "full", "full", "rootFirst", "evaluator", "regex"));
        converter.start();
        
        Assert.assertEquals(ShortenedThrowableConverter.FULL_MAX_DEPTH_PER_THROWABLE, converter.getMaxDepthPerThrowable());
        Assert.assertEquals(ShortenedThrowableConverter.FULL_CLASS_NAME_LENGTH, converter.getShortenedClassNameLength());
        Assert.assertEquals(ShortenedThrowableConverter.FULL_MAX_LENGTH, converter.getMaxLength());
        Assert.assertEquals(true, converter.isRootCauseFirst());
        Assert.assertEquals(evaluator, converter.getEvaluators().get(0));
        Assert.assertEquals("regex", converter.getExcludes().get(0));
        
        // test short values
        converter.setOptionList(Arrays.asList("short", "short", "short", "rootFirst", "evaluator", "regex"));
        converter.start();
        
        Assert.assertEquals(ShortenedThrowableConverter.SHORT_MAX_DEPTH_PER_THROWABLE, converter.getMaxDepthPerThrowable());
        Assert.assertEquals(ShortenedThrowableConverter.SHORT_CLASS_NAME_LENGTH, converter.getShortenedClassNameLength());
        Assert.assertEquals(ShortenedThrowableConverter.SHORT_MAX_LENGTH, converter.getMaxLength());
        
        // test numeric values
        converter.setOptionList(Arrays.asList("1", "2", "3"));
        converter.start();
        Assert.assertEquals(1, converter.getMaxDepthPerThrowable());
        Assert.assertEquals(2, converter.getShortenedClassNameLength());
        Assert.assertEquals(3, converter.getMaxLength());
        
    }

    @Test
    public void testSuppressed() {
        
        try {
            StackTraceElementGenerator.generateSuppressed();
            Assert.fail();
        } catch (RuntimeException e) {
            ShortenedThrowableConverter converter = new ShortenedThrowableConverter();
            converter.setMaxDepthPerThrowable(8);
            String formatted = converter.convert(createEvent(e));
            Assert.assertTrue(formatted.contains("Suppressed"));
            Assert.assertTrue(formatted.contains("common frames omitted"));
        }
    }

    @Test
    public void testShortenedName() {
        
        try {
            StackTraceElementGenerator.generateSingle();
            Assert.fail();
        } catch (RuntimeException e) {
            ShortenedThrowableConverter converter = new ShortenedThrowableConverter();
            converter.setMaxDepthPerThrowable(ShortenedThrowableConverter.FULL_MAX_DEPTH_PER_THROWABLE);
            converter.setShortenedClassNameLength(10);
            String formatted = converter.convert(createEvent(e));
            Assert.assertFalse(formatted.contains(getClass().getPackage().getName()));
            Assert.assertTrue(formatted.contains("n.l.l.s."));
        }
    }

    private String extractClassAndMethod(String string) {
        int atIndex = string.indexOf("at ");
        int endIndex = string.indexOf('(');
        return string.substring(atIndex + 3, endIndex);
    }

    private List<String> getLines(String formatted) {
        List<String> lines = new ArrayList<String>();
        try {
            BufferedReader reader = new BufferedReader(new StringReader(formatted));
            String line = null;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            return lines;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private int countLines(String formatted) {
        return getLines(formatted).size();
    }

    private ILoggingEvent createEvent(RuntimeException e) {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getThrowableProxy()).thenReturn(new ThrowableProxy(e));
        return event;
    }
}
