package org.factcast.store.pgsql.validation;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.LinkedList;

import org.aspectj.lang.ProceedingJoinPoint;
import org.factcast.core.Fact;
import org.factcast.core.FactValidationException;
import org.junit.jupiter.api.Test;

public class FactValidationAspectTest {

	ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
	FactValidator v = mock(FactValidator.class);
	FactValidationAspect uut = new FactValidationAspect(v);
	Fact f = Fact.builder().ns("ns").type("type").version(1).buildWithoutPayload();

	@Test
	void testInterceptPublish() throws Throwable {

		when(jp.getArgs()).thenReturn(new Object[] { Collections.singletonList(f) });
		when(v.validate(f)).thenReturn(new LinkedList());

		Object interceptPublish = uut.interceptPublish(jp);

		verify(jp).proceed();
	}

	@Test
	void testInterceptPublishConditional() throws Throwable {

		when(jp.getArgs()).thenReturn(new Object[] { Collections.singletonList(f) });
		when(v.validate(f)).thenReturn(new LinkedList());

		Object interceptPublish = uut.interceptPublishIfUnchanged(jp);

		verify(jp).proceed();
	}

	@Test
	void testInterceptPublishPropagatesErros() throws Throwable {

		when(jp.getArgs()).thenReturn(new Object[] { Collections.singletonList(f) });
		when(v.validate(f)).thenReturn(Collections.singletonList(new FactValidationError("doing")));

		try {
			Object interceptPublish = uut.interceptPublish(jp);
			fail();
		} catch (FactValidationException e) {
			// expected
		}
		verify(jp, never()).proceed();
	}
}
