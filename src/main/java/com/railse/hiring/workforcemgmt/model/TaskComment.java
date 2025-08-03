package com.railse.hiring.workforcemgmt.model;
import lombok.Data;

@Data
public class TaskComment {
    private Long id;
    private Long taskId;
    private String comment;
    private Long userId;
    private String userName;
    private Long timestamp;

    public TaskComment(Long taskId, String comment, Long userId, String userName) {
        this.taskId = taskId;
        this.comment = comment;
        this.userId = userId;
        this.userName = userName;
        this.timestamp = System.currentTimeMillis();
    }
}
