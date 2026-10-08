package com.controlcenter.github;

/** Starts the CI/CD pipeline that performs the actual rollout to AWS. */
public interface WorkflowDispatcher {

    DispatchResult dispatch(WorkflowDispatchRequest request);
}
