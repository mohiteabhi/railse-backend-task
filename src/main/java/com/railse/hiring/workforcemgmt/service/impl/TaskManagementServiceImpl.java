package com.railse.hiring.workforcemgmt.service.impl;

import com.railse.hiring.workforcemgmt.common.exception.ResourceNotFoundException;
import com.railse.hiring.workforcemgmt.dto.*;
import com.railse.hiring.workforcemgmt.mapper.ITaskManagementMapper;
import com.railse.hiring.workforcemgmt.model.TaskActivity;
import com.railse.hiring.workforcemgmt.model.TaskComment;
import com.railse.hiring.workforcemgmt.model.TaskManagement;
import com.railse.hiring.workforcemgmt.model.enums.Priority;
import com.railse.hiring.workforcemgmt.model.enums.Task;
import com.railse.hiring.workforcemgmt.model.enums.TaskStatus;
import com.railse.hiring.workforcemgmt.repository.TaskRepository;
import com.railse.hiring.workforcemgmt.service.TaskManagementService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TaskManagementServiceImpl implements TaskManagementService {

    private final TaskRepository taskRepository;
    private final ITaskManagementMapper taskMapper;

    public TaskManagementServiceImpl(TaskRepository taskRepository, ITaskManagementMapper taskMapper) {
        this.taskRepository = taskRepository;
        this.taskMapper = taskMapper;
    }

    @Override
    public TaskManagementDto findTaskById(Long id) {
        TaskManagement task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));
        return taskMapper.modelToDto(task);
    }

    @Override
    public List<TaskManagementDto> createTasks(TaskCreateRequest createRequest) {
        List<TaskManagement> createdTasks = new ArrayList<>();
        for (TaskCreateRequest.RequestItem item : createRequest.getRequests()) {
            TaskManagement newTask = new TaskManagement();
            newTask.setReferenceId(item.getReferenceId());
            newTask.setReferenceType(item.getReferenceType());
            newTask.setTask(item.getTask());
            newTask.setAssigneeId(item.getAssigneeId());
            newTask.setPriority(item.getPriority());
            newTask.setTaskDeadlineTime(item.getTaskDeadlineTime());
            newTask.setStatus(TaskStatus.ASSIGNED);
            newTask.setDescription("New task created.");
            createdTasks.add(taskRepository.save(newTask));
        }
        return taskMapper.modelListToDtoList(createdTasks);
    }

    @Override
    public List<TaskManagementDto> updateTasks(UpdateTaskRequest updateRequest) {
        List<TaskManagement> updatedTasks = new ArrayList<>();
        for (UpdateTaskRequest.RequestItem item : updateRequest.getRequests()) {
            TaskManagement task = taskRepository.findById(item.getTaskId())
                    .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + item.getTaskId()));

            if (item.getTaskStatus() != null) {
                TaskStatus oldStatus = task.getStatus();
                task.setStatus(item.getTaskStatus());

                // Add activity for status change
                TaskActivity statusActivity = new TaskActivity(task.getId(),
                        "Status changed from " + oldStatus + " to " + item.getTaskStatus(), 1L, "User");
                task.getActivities().add(statusActivity);

                // Set startedAt if status is STARTED
                if (item.getTaskStatus() == TaskStatus.STARTED && task.getStartedAt() == null) {
                    task.setStartedAt(System.currentTimeMillis());
                }
            }
            if (item.getDescription() != null) {
                task.setDescription(item.getDescription());

                // Add activity for description change
                TaskActivity descActivity = new TaskActivity(task.getId(),
                        "Description updated", 1L, "User");
                task.getActivities().add(descActivity);
            }
            updatedTasks.add(taskRepository.save(task));
        }
        return taskMapper.modelListToDtoList(updatedTasks);
    }

    @Override
    public String assignByReference(AssignByReferenceRequest request) {
        List<Task> applicableTasks = Task.getTasksByReferenceType(request.getReferenceType());
        List<TaskManagement> existingTasks = taskRepository.findByReferenceIdAndReferenceType(
                request.getReferenceId(), request.getReferenceType());

        for (Task taskType : applicableTasks) {
            List<TaskManagement> tasksOfType = existingTasks.stream()
                    .filter(t -> t.getTask() == taskType && t.getStatus() != TaskStatus.COMPLETED)
                    .collect(Collectors.toList());

            // BUG #1 - It should assign to one and cancel the rest, Instead, it reassigns ALL of them
            if (!tasksOfType.isEmpty()) {
//                for (TaskManagement taskToUpdate : tasksOfType) {
//                    taskToUpdate.setAssigneeId(request.getAssigneeId());
//
//                    TaskActivity reassignActivity = new TaskActivity(taskToUpdate.getId(),
//                            "Task reassigned to user " + request.getAssigneeId(), 1L, "Manager");
//                    taskToUpdate.getActivities().add(reassignActivity);
//
//                    taskRepository.save(taskToUpdate);
//                }

                //solution:
                TaskManagement taskToAssign = tasksOfType.get(0);
                taskToAssign.setAssigneeId(request.getAssigneeId());

                TaskActivity reassignActivity = new TaskActivity(taskToAssign.getId(),
                        "Task reassigned to user " + request.getAssigneeId(), 1L, "Manager");
                taskToAssign.getActivities().add(reassignActivity);

                taskRepository.save(taskToAssign);

                // Cancel all other tasks of the same type
                for (int i = 1; i < tasksOfType.size(); i++) {
                    TaskManagement taskToCancel = tasksOfType.get(i);
                    taskToCancel.setStatus(TaskStatus.CANCELLED);

                    // Add activity for cancellation
                    TaskActivity cancelActivity = new TaskActivity(taskToCancel.getId(),
                            "Task cancelled due to reassignment", 1L, "System");
                    taskToCancel.getActivities().add(cancelActivity);

                    taskRepository.save(taskToCancel);
                }
            }
            else {
                // Create a new task if none exist
                TaskManagement newTask = new TaskManagement();
                newTask.setReferenceId(request.getReferenceId());
                newTask.setReferenceType(request.getReferenceType());
                newTask.setTask(taskType);
                newTask.setAssigneeId(request.getAssigneeId());
                newTask.setStatus(TaskStatus.ASSIGNED);
                newTask.setDescription("Task assigned via reference");
                newTask.setPriority(Priority.MEDIUM); // Default priority
                newTask.setTaskDeadlineTime(System.currentTimeMillis() + 86400000);
                taskRepository.save(newTask);
            }
        }
        return "Tasks assigned successfully for reference " + request.getReferenceId();
    }

    @Override
    public List<TaskManagementDto> fetchTasksByDate(TaskFetchByDateRequest request) {
        List<TaskManagement> tasks = taskRepository.findByAssigneeIdIn(request.getAssigneeIds());

        // BUG 2 - It should filter out CANCELLED tasks but doesn't
//        List<TaskManagement> filteredTasks = tasks.stream()
//                .filter(task -> {
//                    // Current buggy logic - doesn't filter by date or exclude cancelled
//                    return true; // This should have proper date filtering and exclude CANCELLED
//                })
//                .collect(Collectors.toList());

        // solution for BUG #2 and FEATURE 1: Proper filtering and smart daily view
        List<TaskManagement> filteredTasks = tasks.stream()
                .filter(task -> {
                    // Exclude cancelled tasks (Bug #2 fix)
                    if (task.getStatus() == TaskStatus.CANCELLED) {
                        return false;
                    }

                    // Feature #1: Smart daily task view
                    Long taskCreatedAt = task.getCreatedAt();
                    Long taskStartedAt = task.getStartedAt();

                    // Include if task was created within the date range
                    if (taskCreatedAt != null &&
                            taskCreatedAt >= request.getStartDate() &&
                            taskCreatedAt <= request.getEndDate()) {
                        return true;
                    }

                    // Include if task started within the date range
                    if (taskStartedAt != null &&
                            taskStartedAt >= request.getStartDate() &&
                            taskStartedAt <= request.getEndDate()) {
                        return true;
                    }

                    // Include if task was created before the range but is still active (not completed)
                    if (taskCreatedAt != null &&
                            taskCreatedAt < request.getStartDate() &&
                            (task.getStatus() == TaskStatus.ASSIGNED || task.getStatus() == TaskStatus.STARTED)) {
                        return true;
                    }

                    return false;
                })
                .collect(Collectors.toList());

        return taskMapper.modelListToDtoList(filteredTasks);
    }

    @Override
    public TaskManagementDto updateTaskPriority(UpdatePriorityRequest request) {
        TaskManagement task = taskRepository.findById(request.getTaskId())
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + request.getTaskId()));

        Priority oldPriority = task.getPriority();
        task.setPriority(request.getPriority());

        // Add activity for priority change
        TaskActivity priorityActivity = new TaskActivity(task.getId(),
                "Priority changed from " + oldPriority + " to " + request.getPriority(), 1L, "Manager");
        task.getActivities().add(priorityActivity);

        return taskMapper.modelToDto(taskRepository.save(task));
    }

    @Override
    public List<TaskManagementDto> findTasksByPriority(Priority priority) {
        return taskMapper.modelListToDtoList(taskRepository.findByPriority(priority));
    }

    @Override
    public TaskManagementDto addComment(AddCommentRequest request) {
        TaskManagement task = taskRepository.findById(request.getTaskId())
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + request.getTaskId()));

        TaskComment comment = new TaskComment(request.getTaskId(), request.getComment(),
                request.getUserId(), request.getUserName());
        task.getComments().add(comment);

        // Add activity for comment addition
        TaskActivity commentActivity = new TaskActivity(task.getId(),
                "Comment added by " + request.getUserName(), request.getUserId(), request.getUserName());
        task.getActivities().add(commentActivity);

        return taskMapper.modelToDto(taskRepository.save(task));
    }
}
