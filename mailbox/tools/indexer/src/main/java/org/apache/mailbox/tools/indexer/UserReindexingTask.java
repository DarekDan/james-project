/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.mailbox.tools.indexer;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.indexer.ReIndexingExecutionFailures;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import reactor.core.publisher.Mono;

public class UserReindexingTask implements Task {

    public static final TaskType USER_RE_INDEXING = TaskType.of("user-reindexing");

    public static class AdditionalInformation extends ReprocessingContextInformation {
        private final Username username;

        AdditionalInformation(Username username, int successfullyReprocessedMailCount, int failedReprocessedMailCount, ReIndexingExecutionFailures failures, Instant timestamp) {
            super(successfullyReprocessedMailCount, failedReprocessedMailCount, failures, timestamp);
            this.username = username;
        }

        public String getUsername() {
            return username.asString();
        }

    }

    private final ReIndexerPerformer reIndexerPerformer;
    private final Username username;
    private final ReprocessingContext reprocessingContext;

    @Inject
    public UserReindexingTask(ReIndexerPerformer reIndexerPerformer, Username username) {
        this.reIndexerPerformer = reIndexerPerformer;
        this.username = username;
        this.reprocessingContext = new ReprocessingContext();
    }

    public static class Factory {

        private final ReIndexerPerformer reIndexerPerformer;

        @Inject
        public Factory(ReIndexerPerformer reIndexerPerformer) {
            this.reIndexerPerformer = reIndexerPerformer;
        }

        public UserReindexingTask create(UserReindexingTaskDTO dto) {
            Username username = Username.of(dto.getUsername());
            return new UserReindexingTask(reIndexerPerformer, username);
        }
    }

    @Override
    public Result run() {
        return reIndexerPerformer.reIndex(username, reprocessingContext)
            .onErrorResume(e -> Mono.just(Result.PARTIAL))
            .block();
    }

    public Username getUsername() {
        return username;
    }

    @Override
    public TaskType type() {
        return USER_RE_INDEXING;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(new UserReindexingTask.AdditionalInformation(username,
            reprocessingContext.successfullyReprocessedMailCount(),
            reprocessingContext.failedReprocessingMailCount(),
            reprocessingContext.failures(),
            Clock.systemUTC().instant())
        );
    }
}
