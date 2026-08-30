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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// The CRUD the UI uses to manage the AI knowledge base:
//   - "knowledge": one row per table (what it is, its columns, hints)
//   - "examples":  question -> correct SQL pairs
// Simple create / list / get / update / delete for both.
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final AiKnowledgeRepository knowledgeRepository;
    private final AiPromptExampleRepository exampleRepository;

    // ---------- KNOWLEDGE (one row per table) ----------

    public AiKnowledgeResponse createKnowledge(AiKnowledgeRequest request) {
        // one row per table - no duplicates
        if (knowledgeRepository.existsByTableName(request.getTableName())) {
            throw new IllegalArgumentException(
                    "Knowledge for table '" + request.getTableName() + "' already exists.");
        }
        AiKnowledge knowledge = new AiKnowledge();
        knowledge.setTableName(request.getTableName());
        knowledge.setPurpose(request.getPurpose());
        knowledge.setColumnsInfo(request.getColumnsInfo());
        knowledge.setHints(request.getHints());
        knowledge.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        return AiKnowledgeResponse.from(knowledgeRepository.save(knowledge));
    }

    @Transactional(readOnly = true)
    public List<AiKnowledgeResponse> listKnowledge() {
        return knowledgeRepository.findAll().stream()
                .map(AiKnowledgeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AiKnowledgeResponse getKnowledge(Integer id) {
        return AiKnowledgeResponse.from(findKnowledge(id));
    }

    public AiKnowledgeResponse updateKnowledge(Integer id, AiKnowledgeRequest request) {
        AiKnowledge knowledge = findKnowledge(id);
        knowledge.setTableName(request.getTableName());
        knowledge.setPurpose(request.getPurpose());
        knowledge.setColumnsInfo(request.getColumnsInfo());
        knowledge.setHints(request.getHints());
        if (request.getEnabled() != null) {
            knowledge.setEnabled(request.getEnabled());
        }
        return AiKnowledgeResponse.from(knowledgeRepository.save(knowledge));
    }

    public void deleteKnowledge(Integer id) {
        knowledgeRepository.delete(findKnowledge(id)); // soft delete via @SQLDelete
    }

    private AiKnowledge findKnowledge(Integer id) {
        return knowledgeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge not found: " + id));
    }

    // ---------- EXAMPLES (question -> SQL) ----------

    public AiPromptExampleResponse createExample(AiPromptExampleRequest request) {
        AiPromptExample example = new AiPromptExample();
        example.setQuestion(request.getQuestion());
        example.setGeneratedSql(request.getGeneratedSql());
        example.setCategory(request.getCategory());
        example.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        return AiPromptExampleResponse.from(exampleRepository.save(example));
    }

    @Transactional(readOnly = true)
    public List<AiPromptExampleResponse> listExamples() {
        return exampleRepository.findAll().stream()
                .map(AiPromptExampleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AiPromptExampleResponse getExample(Integer id) {
        return AiPromptExampleResponse.from(findExample(id));
    }

    public AiPromptExampleResponse updateExample(Integer id, AiPromptExampleRequest request) {
        AiPromptExample example = findExample(id);
        example.setQuestion(request.getQuestion());
        example.setGeneratedSql(request.getGeneratedSql());
        example.setCategory(request.getCategory());
        if (request.getEnabled() != null) {
            example.setEnabled(request.getEnabled());
        }
        return AiPromptExampleResponse.from(exampleRepository.save(example));
    }

    public void deleteExample(Integer id) {
        exampleRepository.delete(findExample(id));
    }

    private AiPromptExample findExample(Integer id) {
        return exampleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Example not found: " + id));
    }
}
