package cn.edu.xmu.clonefactory;

import cn.edu.xmu.clonefactory.fixture.FinalTarget;
import cn.edu.xmu.clonefactory.fixture.MutableTarget;
import cn.edu.xmu.clonefactory.fixture.MutableTarget2;
import cn.edu.xmu.clonefactory.fixture.NotNullRecordTarget;
import cn.edu.xmu.clonefactory.fixture.PartialSource;
import cn.edu.xmu.clonefactory.fixture.RecordSource;
import cn.edu.xmu.clonefactory.fixture.RecordTarget;
import cn.edu.xmu.clonefactory.fixture.SourceBean;
import cn.edu.xmu.clonefactory.util.CloneFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CloneFactoryTest {

    @Test
    void copiesToMutableTargetUsingSetters() {
        SourceBean source = new SourceBean("apple", 10);
        MutableTarget target = new MutableTarget();

        MutableTarget result = CloneFactory.copy(target, source);

        assertEquals("apple", result.getName());
        assertEquals(10, result.getAge());
    }

    @Test
    void copiesToRecordUsingCanonicalConstructorAndKeepsUnmatchedTargetValues() {
        SourceBean source = new SourceBean("apple", 10);
        RecordTarget target = new RecordTarget("old", 1, "keep");

        RecordTarget result = CloneFactory.copy(target, source);

        assertEquals("apple", result.name());
        assertEquals(10, result.age());
        assertEquals("keep", result.extra());
    }

    @Test
    void copiesToClassWithFinalFieldsUsingConstructor() {
        SourceBean source = new SourceBean("apple", 10);
        FinalTarget target = new FinalTarget("old", 1, "keep");

        FinalTarget result = CloneFactory.copy(target, source);

        assertEquals("apple", result.getName());
        assertEquals(10, result.getAge());
        assertEquals("keep", result.getExtra());
    }

    @Test
    void supportsRecordSourceViaCopyTo() {
        RecordSource source = new RecordSource("apple", 10);
        MutableTarget2 target = new MutableTarget2();

        MutableTarget2 result = CloneFactory.copy(target, source);

        assertEquals("apple", result.getName());
        assertEquals(10, result.getAge());
    }

    @Test
    void copyNotNullKeepsTargetValueWhenSourceValueIsNull() {
        PartialSource source = new PartialSource(null, 20);
        NotNullRecordTarget target = new NotNullRecordTarget("base", 1);

        NotNullRecordTarget result = CloneFactory.copyNotNull(target, source);

        assertEquals("base", result.name());
        assertEquals(20, result.age());
    }
}