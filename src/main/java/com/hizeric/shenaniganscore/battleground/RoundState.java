package com.hizeric.shenaniganscore.battleground;

/** Lifecycle of a battleground round. */
public enum RoundState {
    /** No arena configured or no round started. */
    IDLE,
    /** Fighting; the round timer is counting down. */
    RUNNING,
    /** Last few seconds, announced. */
    WARNING,
    /** Everyone is staged; the arena is being restored. Shopping is available here. */
    STAGING,
    /** Restore in progress; Deploy is disabled until it finishes. */
    RESTORING
}
