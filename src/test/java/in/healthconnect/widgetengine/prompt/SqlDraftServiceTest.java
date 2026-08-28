package in.healthconnect.widgetengine.prompt;

import in.healthconnect.widgetengine.dto.response.GeneratedQueryResponse;
import in.healthconnect.widgetengine.engine.SqlSafetyGuard;
import in.healthconnect.widgetengine.entity.AiPromptExample;
import in.healthconnect.widgetengine.entity.Widget;
import in.healthconnect.widgetengine.entity.enums.WidgetModule;
import in.healthconnect.widgetengine.entity.enums.WidgetStatus;
import in.healthconnect.widgetengine.repository.AiKnowledgeRepository;
import in.healthconnect.widgetengine.repository.AiPromptExampleRepository;
import in.healthconnect.widgetengine.repository.WidgetRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Tests for SqlDraftService - the piece that turns a question into a stored DRAFT widget.
// Real PromptBuilder / SqlCleaner / SqlSafetyGuard; MOCK repositories and a MOCK AI.
class SqlDraftServiceTest {

    private final AiKnowledgeRepository knowledgeRepository = mock(AiKnowledgeRepository.class);
    private final AiPromptExampleRepository exampleRepository = mock(AiPromptExampleRepository.class);
    private final WidgetRepository widgetRepository = mock(WidgetRepository.class);
    private final QueryGenerator queryGenerator = mock(QueryGenerator.class);

    private final SqlDraftService service = new SqlDraftService(
            knowledgeRepository, exampleRepository, widgetRepository,
            new PromptBuilder(), new SqlCleaner(), new SqlSafetyGuard(), queryGenerator);

    @Test
    void generatesFromAiCleansAndStoresAsDraft() {
        when(knowledgeRepository.findByEnabledTrueOrderByTableNameAsc()).thenReturn(List.of());
        when(exampleRepository.findByEnabledTrue()).thenReturn(List.of());
        when(widgetRepository.countByCodeIncludingDeleted(any())).thenReturn(0L);
        when(widgetRepository.save(any(Widget.class))).thenAnswer(call -> call.getArgument(0));
        // AI returns SQL wrapped in a code fence - the cleaner must strip it
        when(queryGenerator.generateSql(any()))
                .thenReturn("```sql\nSELECT count(*) AS total FROM doctors WHERE is_deleted = false\n```");

        GeneratedQueryResponse response = service.generateDraft("count all doctors", null);

        assertEquals("SELECT count(*) AS total FROM doctors WHERE is_deleted = false", response.getSql());
        assertEquals(WidgetStatus.DRAFT, response.getStatus());
        assertEquals("count all doctors", response.getQuestion());

        // stored as a PROMPT widget, DRAFT
        ArgumentCaptor<Widget> captor = ArgumentCaptor.forClass(Widget.class);
        verify(widgetRepository).save(captor.capture());
        assertEquals(WidgetModule.PROMPT, captor.getValue().getModule());
        assertEquals(WidgetStatus.DRAFT, captor.getValue().getStatus());
    }

    @Test
    void reusesExactExampleWithoutCallingAi() {
        AiPromptExample example = new AiPromptExample();
        example.setQuestion("count all doctors");
        example.setGeneratedSql("SELECT count(*) FROM doctors");
        example.setEnabled(true);
        when(exampleRepository.findByEnabledTrue()).thenReturn(List.of(example));
        when(widgetRepository.countByCodeIncludingDeleted(any())).thenReturn(0L);
        when(widgetRepository.save(any(Widget.class))).thenAnswer(call -> call.getArgument(0));

        // note: different case + extra spaces, should still match
        GeneratedQueryResponse response = service.generateDraft("  Count All Doctors ", null);

        assertEquals("SELECT count(*) FROM doctors", response.getSql());
        verify(queryGenerator, never()).generateSql(any()); // AI must NOT be called
    }

    @Test
    void rejectsNonSelectFromAi() {
        when(knowledgeRepository.findByEnabledTrueOrderByTableNameAsc()).thenReturn(List.of());
        when(exampleRepository.findByEnabledTrue()).thenReturn(List.of());
        when(queryGenerator.generateSql(any())).thenReturn("DELETE FROM doctors");

        assertThrows(IllegalArgumentException.class, () -> service.generateDraft("remove doctors", null));
    }

    @Test
    void aCodeTakenByASOFTDELETEDWidgetIsSkipped() {
        // The bug this guards: Widget has @SQLRestriction("is_deleted = false"), so
        // existsByCode cannot see deleted rows - but MySQL's uk_widget_code unique key can.
        // Asking the same question again after deleting its widget used to pass the
        // uniqueness loop and then blow up on INSERT with a duplicate-key error.
        when(knowledgeRepository.findByEnabledTrueOrderByTableNameAsc()).thenReturn(List.of());
        when(exampleRepository.findByEnabledTrue()).thenReturn(List.of());
        when(queryGenerator.generateSql(any()))
                .thenReturn("SELECT count(*) AS total FROM doctors WHERE is_deleted = false");
        when(widgetRepository.save(any(Widget.class))).thenAnswer(call -> call.getArgument(0));

        // the plain code is taken by a deleted row; the -2 variant is free
        when(widgetRepository.countByCodeIncludingDeleted("count-all-doctors")).thenReturn(1L);
        when(widgetRepository.countByCodeIncludingDeleted("count-all-doctors-2")).thenReturn(0L);

        service.generateDraft("count all doctors", null);

        ArgumentCaptor<Widget> captor = ArgumentCaptor.forClass(Widget.class);
        verify(widgetRepository).save(captor.capture());
        assertEquals("count-all-doctors-2", captor.getValue().getCode());
    }

    @Test
    void aGivenTitleBecomesTheWidgetName() {
        when(knowledgeRepository.findByEnabledTrueOrderByTableNameAsc()).thenReturn(List.of());
        when(exampleRepository.findByEnabledTrue()).thenReturn(List.of());
        when(widgetRepository.countByCodeIncludingDeleted(any())).thenReturn(0L);
        when(widgetRepository.save(any(Widget.class))).thenAnswer(call -> call.getArgument(0));
        when(queryGenerator.generateSql(any()))
                .thenReturn("SELECT count(*) AS total FROM doctors WHERE is_deleted = false");

        GeneratedQueryResponse response =
                service.generateDraft("count all the doctors we currently employ", "Doctor Headcount");

        ArgumentCaptor<Widget> captor = ArgumentCaptor.forClass(Widget.class);
        verify(widgetRepository).save(captor.capture());
        // the title is what a person sees...
        assertEquals("Doctor Headcount", captor.getValue().getName());
        assertEquals("Doctor Headcount", response.getName());
        // ...while the question is kept as the description, so we still know what was asked
        assertEquals("count all the doctors we currently employ", captor.getValue().getDescription());
        assertEquals("count all the doctors we currently employ", response.getQuestion());
    }

    @Test
    void withoutATitleTheQuestionIsStillTheName() {
        // the behaviour before the title field existed - nothing should have regressed
        when(knowledgeRepository.findByEnabledTrueOrderByTableNameAsc()).thenReturn(List.of());
        when(exampleRepository.findByEnabledTrue()).thenReturn(List.of());
        when(widgetRepository.countByCodeIncludingDeleted(any())).thenReturn(0L);
        when(widgetRepository.save(any(Widget.class))).thenAnswer(call -> call.getArgument(0));
        when(queryGenerator.generateSql(any()))
                .thenReturn("SELECT count(*) AS total FROM doctors WHERE is_deleted = false");

        service.generateDraft("count all doctors", null);

        ArgumentCaptor<Widget> captor = ArgumentCaptor.forClass(Widget.class);
        verify(widgetRepository).save(captor.capture());
        assertEquals("count all doctors", captor.getValue().getName());
    }

    @Test
    void aBlankTitleFallsBackToTheQuestion() {
        // an untouched input box sends "   ", not null
        when(knowledgeRepository.findByEnabledTrueOrderByTableNameAsc()).thenReturn(List.of());
        when(exampleRepository.findByEnabledTrue()).thenReturn(List.of());
        when(widgetRepository.countByCodeIncludingDeleted(any())).thenReturn(0L);
        when(widgetRepository.save(any(Widget.class))).thenAnswer(call -> call.getArgument(0));
        when(queryGenerator.generateSql(any()))
                .thenReturn("SELECT count(*) AS total FROM doctors WHERE is_deleted = false");

        service.generateDraft("count all doctors", "   ");

        ArgumentCaptor<Widget> captor = ArgumentCaptor.forClass(Widget.class);
        verify(widgetRepository).save(captor.capture());
        assertEquals("count all doctors", captor.getValue().getName());
    }
}
