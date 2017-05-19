package org.factcast.server.rest.resources;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;

import java.util.List;

import javax.ws.rs.BadRequestException;

import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;

@RunWith(MockitoJUnitRunner.class)
public class FactsTransactionsResourceTest {
    @Mock
    private FactStore factStore;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private FactsTransactionsResource uut;

    @Test
    public void testNewTransaction() throws Exception {
        FactTransactionJson factTransactionJson = objectMapper.readValue(this.getClass()
                .getResourceAsStream("TransactionJson.json"), FactTransactionJson.class);
        uut.newTransaction(factTransactionJson);
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(factStore).publish(captor.capture());

        List<Fact> facts = captor.getValue();
        Fact fact = facts.get(0);
        FactJson sentFact = factTransactionJson.facts().get(0);
        assertEquals(sentFact.header().ns(), fact.ns());
        assertEquals(sentFact.header().aggIds(), fact.aggIds());
        assertEquals(sentFact.header().type(), fact.type());
        assertEquals(sentFact.header().id(), fact.id());
        assertEquals(objectMapper.writeValueAsString(sentFact.header()), fact.jsonHeader());
        assertEquals(sentFact.payload().toString(), fact.jsonPayload());
    }

    @Test(expected = BadRequestException.class)
    public void testNewTransactionWithNullPayload() throws Exception {
        FactTransactionJson factTransactionJson = objectMapper.readValue(this.getClass()
                .getResourceAsStream("TransactionJson.json"), FactTransactionJson.class);
        factTransactionJson.facts().get(0).payload(NullNode.instance);
        uut.newTransaction(factTransactionJson);
    }

}
