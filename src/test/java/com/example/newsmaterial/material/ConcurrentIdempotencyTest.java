package com.example.newsmaterial.material;

import com.example.newsmaterial.NewsMaterialApplication;
import com.example.newsmaterial.material.api.MaterialApi.MaterialView;
import com.example.newsmaterial.material.api.MaterialApi.TextMaterialRequest;
import com.example.newsmaterial.material.api.MaterialApi.TextVersionRequest;
import com.example.newsmaterial.material.service.MaterialService;
import com.example.newsmaterial.project.ProjectRequest;
import com.example.newsmaterial.project.ProjectService;
import com.example.newsmaterial.security.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = NewsMaterialApplication.class)
@ActiveProfiles("local")
class ConcurrentIdempotencyTest {
    @Autowired private MaterialService materials;
    @Autowired private ProjectService projects;
    @Autowired private UserRepository users;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    @Test
    void simultaneousSameKeyTextCreatesReplayOneMaterial() throws Exception {
        UUID ownerId = users.findByUsername("emma").orElseThrow().getId();
        UUID projectId = projects.create(ownerId, new ProjectRequest("Concurrent create")).id();
        TextMaterialRequest request = new TextMaterialRequest(projectId, "Flood note", null, null, List.of(), "Account");

        List<MaterialView> results = concurrently(() -> materials.createText(ownerId, "same-create-key", request));

        assertThat(results).extracting(MaterialView::id).containsOnly(results.getFirst().id());
        assertThat(results).extracting(view -> view.version().versionNumber()).containsOnly(1);
    }

    @Test
    void simultaneousSameKeyVersionsReplayOneNextVersion() throws Exception {
        UUID ownerId = users.findByUsername("emma").orElseThrow().getId();
        UUID projectId = projects.create(ownerId, new ProjectRequest("Concurrent version")).id();
        MaterialView initial = materials.createText(ownerId, "initial-" + UUID.randomUUID(),
                new TextMaterialRequest(projectId, "Flood note", null, null, List.of(), "Account"));
        TextVersionRequest request = new TextVersionRequest(1, "Updated note", null, null, List.of(), "Updated account");

        List<MaterialView> results = concurrently(() -> materials.createVersion(initial.id(), ownerId, "same-version-key", request));

        assertThat(results).extracting(view -> view.version().versionNumber()).containsOnly(2);
        assertThat(results).extracting(view -> view.version().id()).containsOnly(results.getFirst().version().id());
    }

    private <T> List<T> concurrently(Callable<T> operation) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<T> gated = () -> {
            ready.countDown();
            start.await();
            return operation.call();
        };
        Future<T> first = executor.submit(gated);
        Future<T> second = executor.submit(gated);
        ready.await();
        start.countDown();
        return List.of(first.get(), second.get());
    }
}
