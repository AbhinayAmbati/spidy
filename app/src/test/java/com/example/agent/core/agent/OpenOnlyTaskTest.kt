package com.example.agent.core.agent

import com.example.agent.domain.model.StepStatus
import com.example.agent.domain.model.TaskStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenOnlyTaskTest {
    private val opened = listOf(TaskStep(0, "Opening YouTube", StepStatus.Done))

    @Test fun openTaskIsDoneOnceTheAppOpened() =
        assertTrue(OpenOnlyTask.isSatisfied("Open YouTube", setOf("open_app"), opened))

    @Test fun otherTasksKeepGoing() =
        assertFalse(OpenOnlyTask.isSatisfied("Search cats on YouTube", setOf("open_app"), opened))

    @Test fun aFailedOpenIsNotDone() = assertFalse(
        OpenOnlyTask.isSatisfied("Open YouTube", setOf("open_app"), listOf(TaskStep(0, "Opening YouTube", StepStatus.Failed))),
    )

    @Test fun nothingDoneYetIsNotDone() =
        assertFalse(OpenOnlyTask.isSatisfied("Open YouTube", emptySet(), emptyList()))

    @Test fun summaryIsTheLastStep() = assertEquals("Opening YouTube", OpenOnlyTask.summary(opened))
}
