package com.logmaster.task;

import com.logmaster.domain.TieredTaskList;
import com.logmaster.util.FileUtils;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static com.logmaster.util.GsonOverride.GSON;

@Slf4j
@Singleton
public class TaskListStorage {
    private static final String LOCAL_TASK_LIST_FILE = "task-list.json";

    private static final String REMOTE_TASK_LIST_URL = "https://raw.githubusercontent.com/OSRS-Taskman/task-list/refs/heads/main/lists/tedious.json";

    @Inject
    private OkHttpClient client;

    private TieredTaskList taskList;

    @Inject
    public TaskListStorage(OkHttpClient client) {
        this.client = client;

        loadAsync();
    }

    public TieredTaskList get() {
        return taskList;
    }

    private void loadAsync() {
        fetchRemoteAsync()
                .exceptionally(t -> fetchLocal())
                .thenAccept(taskList -> this.taskList = taskList);
    }

    private static TieredTaskList fetchLocal() {
        return FileUtils.loadDefinitionResource(TieredTaskList.class, LOCAL_TASK_LIST_FILE);
    }

    private CompletableFuture<TieredTaskList> fetchRemoteAsync() {
        return requestRemote().thenApply(response -> {
            try (Response res = response) {
                ResponseBody body = res.body();
                if (body == null) {
                    throw new RuntimeException("Response body is null");
                }

                return GSON.fromJson(body.string(), TieredTaskList.class);
            } catch (Exception e) {
                log.error("Error!", e);
                return null;
            }
        });
    }

    private CompletableFuture<Response> requestRemote() {
        Request request = new Request.Builder()
                .url(REMOTE_TASK_LIST_URL)
                .header("Content-Type", "application/json")
                .build();

        CompletableFuture<Response> future = new CompletableFuture<>();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                log.error("Failed requesting remote task list", e);
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                future.complete(response);
            }
        });
        return future;
    }
}
