package in.healthconnect.widgetengine.service;

import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.widgetengine.dto.request.AiKnowledgeRequest;
import in.healthconnect.widgetengine.dto.request.AiPromptExampleRequest;
import in.healthconnect.widgetengine.dto.response.AiKnowledgeResponse;
import in.healthconnect.widgetengine.dto.response.AiPromptExampleResponse;
import in.healthconnect.widgetengine.entity.AiKnowledge;
import in.healthconnect.widgetengine.entity.AiPromptExample;
import in.healthconnect.widgetengine.repository.AiKnowledgeRepository;
import in.healthconnect.widgetengine.repository.AiPromptExampleRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Tests for KnowledgeBaseService - the CRUD the UI uses to manage the AI knowledge base.
// Both repositories are mocked (no database needed).
class KnowledgeBaseServiceTest {

    private final AiKnowledgeRepository knowledgeRepository = mock(AiKnowledgeRepository.class);
    private final AiPromptExampleRepository exampleRepository = mock(AiPromptExampleRepository.class);
    private final KnowledgeBaseService service =
            new KnowledgeBaseService(knowledgeRepository, exampleRepository);

    private AiKnowledgeRequest knowledgeRequest() {
        AiKnowledgeRequest request = new AiKnowledgeRequest();
        request.setTableName("doctors");
        request.setPurpose("Doctors in the hospital");
        request.setColumnsInfo("id, first_name, last_name, status, is_deleted");
        return request;
    }

    @Test
    void createKnowledgeRejectsDuplicateTable() {
        when(knowledgeRepository.existsByTableName("doctors")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.createKnowledge(knowledgeRequest()));
    }

    @Test
    void createKnowledgeSaves() {
        when(knowledgeRepository.existsByTableName(any())).thenReturn(false);
        when(knowledgeRepository.save(any(AiKnowledge.class))).thenAnswer(call -> call.getArgument(0));

        AiKnowledgeResponse response = service.createKnowledge(knowledgeRequest());

        assertEquals("doctors", response.getTableName());
        assertTrue(response.getEnabled()); // defaults to on
        verify(knowledgeRepository).save(any(AiKnowledge.class));
    }

    @Test
    void getKnowledgeThrowsWhenMissing() {
        when(knowledgeRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getKnowledge(99));
    }

    @Test
    void deleteKnowledgeRemoves() {
        AiKnowledge knowledge = new AiKnowledge();
        knowledge.setTableName("doctors");
        when(knowledgeRepository.findById(3)).thenReturn(Optional.of(knowledge));

        service.deleteKnowledge(3);

        verify(knowledgeRepository).delete(knowledge);
    }

    @Test
    void createExampleSaves() {
        AiPromptExampleRequest request = new AiPromptExampleRequest();
        request.setQuestion("count all doctors");
        request.setGeneratedSql("SELECT count(*) AS total FROM doctors WHERE is_deleted = false");
        when(exampleRepository.save(any(AiPromptExample.class))).thenAnswer(call -> call.getArgument(0));

        AiPromptExampleResponse response = service.createExample(request);

        assertEquals("count all doctors", response.getQuestion());
        assertTrue(response.getEnabled());
        verify(exampleRepository).save(any(AiPromptExample.class));
    }
}
