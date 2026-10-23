package com.chunklock.service;

import com.chunklock.model.ChunkClaim;
import com.chunklock.model.ChunkKey;
import com.chunklock.storage.ClaimRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClaimServiceTest {
    private ClaimRepository repository;
    private ClaimService service;

    @BeforeEach
    void setUp() {
        repository = mock(ClaimRepository.class);
        when(repository.save(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(repository.delete(any())).thenReturn(CompletableFuture.completedFuture(null));
        service = new ClaimService(repository);
    }

    @Test
    void loadsClaimsAndCountsByOwner() {
        UUID owner = UUID.randomUUID();
        ChunkClaim first = claim(owner, 1);
        ChunkClaim second = claim(owner, 2);
        ChunkClaim other = claim(UUID.randomUUID(), 3);

        assertFalse(service.isReady());
        service.initialize(List.of(first, second, other));

        assertTrue(service.isReady());
        assertSame(first, service.get(first.key()));
        assertEquals(2, service.countOwned(owner));
        assertTrue(service.owns(first, owner));
        assertFalse(service.owns(first, UUID.randomUUID()));
        assertFalse(service.owns(null, owner));
    }

    @Test
    void createsSavesAndDeletesClaimsThroughRepository() {
        ChunkClaim claim = claim(UUID.randomUUID(), 1);
        when(repository.save(claim)).thenReturn(CompletableFuture.completedFuture(null));

        service.create(claim).join();
        assertSame(claim, service.get(claim.key()));
        verify(repository).save(claim);

        service.save(claim).join();
        verify(repository, times(2)).save(claim);

        service.unclaim(claim.key()).join();
        assertNull(service.get(claim.key()));
        verify(repository).delete(claim.key());
    }

    @Test
    void recordsOwnerLoginAndPersistsTheUpdatedClaim() {
        ChunkClaim claim = claim(UUID.randomUUID(), 1);
        long before = System.currentTimeMillis();

        service.recordOwnerLogin(claim).join();

        assertTrue(claim.lastOwnerLogin() >= before);
        verify(repository).save(claim);
    }

    private static ChunkClaim claim(UUID owner, int x) {
        return ChunkClaim.create(new ChunkKey(UUID.randomUUID(), x, 0), owner);
    }
}
