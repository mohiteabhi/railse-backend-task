package com.railse.hiring.workforcemgmt.model;

import lombok.Data;


@Data
public class TaskActivity {
    private Long id;
    private Long taskId;
    private String activity;
    private Long userId;
    private String userName;
    private Long timestamp;

    public TaskActivity(Long taskId, String activity, Long userId, String userName) {
        this.taskId = taskId;
        this.activity = activity;
        this.userId = userId;
        this.userName = userName;
        this.timestamp = System.currentTimeMillis();
    }
}
