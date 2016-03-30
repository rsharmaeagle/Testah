package org.testah.client.enums;

public enum TestType {

    RETIRE(10, "Test Should be Removed, no longer in use"),

    MANUAL(6, "Test is only used manually, not for automated use"),

    PENDING(3, "Test is inprogress not ready to be run"),

    AUTOMATED(0, "Test is ready to be Automatically Run"),

    DEFAULT(-1, "Used For test cases to take test plans test type");

    final int    priority;
    final String description;

    TestType(final int priority, final String description) {
        this.priority = priority;
        this.description = description;
    }

}
