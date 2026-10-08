package com.controlcenter.github;

public record DispatchResult(Outcome outcome, String message) {

    public enum Outcome {
        /** The workflow run was accepted by GitHub. */
        DISPATCHED,
        /** The integration is switched off; the pipeline must be run and reported manually. */
        SKIPPED,
        /** GitHub rejected the request or was unreachable. */
        FAILED
    }

    public static DispatchResult dispatched(String message) {
        return new DispatchResult(Outcome.DISPATCHED, message);
    }

    public static DispatchResult skipped(String message) {
        return new DispatchResult(Outcome.SKIPPED, message);
    }

    public static DispatchResult failed(String message) {
        return new DispatchResult(Outcome.FAILED, message);
    }
}
