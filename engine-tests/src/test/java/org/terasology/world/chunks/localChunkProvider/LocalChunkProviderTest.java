/*
 * Copyright 2018 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.world.chunks.localChunkProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.Event;
import org.terasology.fixtures.TestBlockManager;
import org.terasology.fixtures.TestChunkStore;
import org.terasology.fixtures.TestStorageManager;
import org.terasology.fixtures.TestWorldGenerator;
import org.terasology.math.JomlUtil;
import org.terasology.math.geom.Vector3i;
import org.terasology.world.BlockEntityRegistry;
import org.terasology.world.block.BeforeDeactivateBlocks;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockManager;
import org.terasology.world.block.BlockRegion;
import org.terasology.world.block.BlockRegionIterable;
import org.terasology.world.block.OnActivatedBlocks;
import org.terasology.world.block.OnAddedBlocks;
import org.terasology.world.chunks.Chunk;
import org.terasology.world.chunks.blockdata.ExtraBlockDataManager;
import org.terasology.world.chunks.event.BeforeChunkUnload;
import org.terasology.world.chunks.event.OnChunkGenerated;
import org.terasology.world.chunks.event.OnChunkLoaded;
import org.terasology.world.chunks.internal.ChunkImpl;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class LocalChunkProviderTest {

    private static final int WAIT_CHUNK_IS_READY_IN_SECONDS = 10;

    private LocalChunkProvider chunkProvider;
    private EntityManager entityManager;
    private BlockManager blockManager;
    private ExtraBlockDataManager extraDataManager;
    private BlockEntityRegistry blockEntityRegistry;
    private EntityRef worldEntity;
    private ChunkCache chunkCache;
    private Block blockAtBlockManager;
    private TestStorageManager storageManager;
    private TestWorldGenerator generator;

    @BeforeEach
    public void setUp() {
        entityManager = mock(EntityManager.class);
        blockAtBlockManager = new Block();
        blockAtBlockManager.setId((short) 1);
        blockAtBlockManager.setUri(BlockManager.AIR_ID);
        blockAtBlockManager.setEntity(mock(EntityRef.class));
        blockManager = new TestBlockManager(blockAtBlockManager);
        extraDataManager = new ExtraBlockDataManager();
        blockEntityRegistry = mock(BlockEntityRegistry.class);
        worldEntity = mock(EntityRef.class);
        chunkCache = new ConcurrentMapChunkCache();
        storageManager = new TestStorageManager();
        generator = new TestWorldGenerator(blockManager);
        chunkProvider = new LocalChunkProvider(storageManager,
                entityManager,
                generator,
                blockManager,
                extraDataManager,
                chunkCache);
        chunkProvider.setBlockEntityRegistry(blockEntityRegistry);
        chunkProvider.setWorldEntity(worldEntity);
        chunkProvider.setRelevanceSystem(new RelevanceSystem(chunkProvider)); // workaround. initialize loading pipeline
    }

    @AfterEach
    public void tearDown() {
        chunkProvider.shutdown();
    }

    private void requestCreatingOrLoadingArea(Vector3i chunkPosition, int radius) {
        BlockRegion extentsRegion = new BlockRegion(
                chunkPosition.x - radius, chunkPosition.y - radius, chunkPosition.z - radius,
                chunkPosition.x + radius, chunkPosition.y + radius, chunkPosition.z + radius);
        BlockRegionIterable.region(extentsRegion).build().iterator().forEachRemaining(chunkPos -> chunkProvider.createOrLoadChunk(JomlUtil.from(chunkPos)));
    }

    private void requestCreatingOrLoadingArea(Vector3i chunkPosition) {
        requestCreatingOrLoadingArea(chunkPosition, 1);
    }

    @Test
    public void testGenerateSingleChunk() {
        Vector3i chunkPosition = new Vector3i(0, 0, 0);
        requestCreatingOrLoadingArea(chunkPosition);
        waitChunkReadyAt(chunkPosition);

        final ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        verify(worldEntity, atLeast(2)).send(eventArgumentCaptor.capture());
        Assertions.assertAll("WorldEvents not valid",
                () -> {
                    Event mustBeOnGeneratedEvent = eventArgumentCaptor.getAllValues().get(0);
                    Assertions.assertTrue(mustBeOnGeneratedEvent instanceof OnChunkGenerated,
                            "First world event must be OnChunkGenerated");
                    Assertions.assertEquals(((OnChunkGenerated) mustBeOnGeneratedEvent).getChunkPos(),
                            chunkPosition,
                            "Chunk position at event not expected");
                },
                () -> {
                    Event mustBeOnLoadedEvent = eventArgumentCaptor.getAllValues().get(1);
                    Assertions.assertTrue(mustBeOnLoadedEvent instanceof OnChunkLoaded,
                            "Second world event must be OnChunkLoaded");
                    Assertions.assertEquals(((OnChunkLoaded) mustBeOnLoadedEvent).getChunkPos(),
                            chunkPosition,
                            "Chunk position at event not expected");
                });
    }

    @Test
    public void testGenerateSingleChunkWithBlockLifeCycle() {
        Vector3i chunkPosition = new Vector3i(0, 0, 0);
        blockAtBlockManager.setLifecycleEventsRequired(true);
        blockAtBlockManager.setEntity(mock(EntityRef.class));
        requestCreatingOrLoadingArea(chunkPosition);
        waitChunkReadyAt(chunkPosition);

        final ArgumentCaptor<Event> worldEventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(worldEntity, atLeast(2)).send(worldEventCaptor.capture());
        Assertions.assertAll("World Events not valid",
                () -> {
                    Event mustBeOnGeneratedEvent = worldEventCaptor.getAllValues().get(0);
                    Assertions.assertTrue(mustBeOnGeneratedEvent instanceof OnChunkGenerated,
                            "First world event must be OnChunkGenerated");
                    Assertions.assertEquals(((OnChunkGenerated) mustBeOnGeneratedEvent).getChunkPos(),
                            chunkPosition,
                            "Chunk position at event not expected");
                },
                () -> {
                    Event mustBeOnLoadedEvent = worldEventCaptor.getAllValues().get(1);
                    Assertions.assertTrue(mustBeOnLoadedEvent instanceof OnChunkLoaded,
                            "Second world event must be OnChunkLoaded");
                    Assertions.assertEquals(((OnChunkLoaded) mustBeOnLoadedEvent).getChunkPos(),
                            chunkPosition,
                            "Chunk position at event not expected");
                });

        //TODO, it is not clear if the activate/addedBlocks event logic is correct.
        //See https://github.com/MovingBlocks/Terasology/issues/3244
        final ArgumentCaptor<Event> blockEventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(blockAtBlockManager.getEntity(), atLeast(1)).send(blockEventCaptor.capture());

        Event mustBeOnActivatedBlocks = blockEventCaptor.getAllValues().get(0);
        Assertions.assertTrue(mustBeOnActivatedBlocks instanceof OnActivatedBlocks,
                "First block event must be OnActivatedBlocks");
        Assertions.assertTrue(((OnActivatedBlocks) mustBeOnActivatedBlocks).blockCount() > 0,
                "Block count on activate must be non zero");
    }

    @Test
    public void testLoadSingleChunk() {
        Vector3i chunkPosition = new Vector3i(0, 0, 0);
        Chunk chunk = new ChunkImpl(chunkPosition, blockManager, extraDataManager);
        generator.createChunk(chunk, null);
        storageManager.add(chunk);

        requestCreatingOrLoadingArea(chunkPosition);
        waitChunkReadyAt(chunkPosition);

        Assertions.assertTrue(((TestChunkStore) storageManager.loadChunkStore(chunkPosition)).isEntityRestored(),
                "Entities must be restored by loading");

        final ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        verify(worldEntity, atLeast(1)).send(eventArgumentCaptor.capture());
        Event mustBeOnLoadedEvent = eventArgumentCaptor.getAllValues().get(0);
        Assertions.assertTrue(mustBeOnLoadedEvent instanceof OnChunkLoaded,
                "Second world event must be OnChunkLoaded");
        Assertions.assertEquals(((OnChunkLoaded) mustBeOnLoadedEvent).getChunkPos(),
                chunkPosition,
                "Chunk position at event not expected");
    }

    @Test
    public void testLoadSingleChunkWithBlockLifecycle() {
        Vector3i chunkPosition = new Vector3i(0, 0, 0);
        Chunk chunk = new ChunkImpl(chunkPosition, blockManager, extraDataManager);
        generator.createChunk(chunk, null);
        storageManager.add(chunk);
        blockAtBlockManager.setLifecycleEventsRequired(true);
        blockAtBlockManager.setEntity(mock(EntityRef.class));

        requestCreatingOrLoadingArea(chunkPosition);
        waitChunkReadyAt(chunkPosition);

        Assertions.assertTrue(((TestChunkStore) storageManager.loadChunkStore(chunkPosition)).isEntityRestored(),
                "Entities must be restored by loading");


        final ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        verify(worldEntity, atLeast(1)).send(eventArgumentCaptor.capture());
        Event mustBeOnLoadedEvent = eventArgumentCaptor.getAllValues().get(0);
        Assertions.assertTrue(mustBeOnLoadedEvent instanceof OnChunkLoaded,
                "Second world event must be OnChunkLoaded");
        Assertions.assertEquals(((OnChunkLoaded) mustBeOnLoadedEvent).getChunkPos(),
                chunkPosition,
                "Chunk position at event not expected");

        //TODO, it is not clear if the activate/addedBlocks event logic is correct.
        //See https://github.com/MovingBlocks/Terasology/issues/3244
        final ArgumentCaptor<Event> blockEventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(blockAtBlockManager.getEntity(), atLeast(2)).send(blockEventCaptor.capture());
        Assertions.assertAll("Block events not valid",
                () -> {
                    Event mustBeOnAddedBlocks = blockEventCaptor.getAllValues().get(0);
                    Assertions.assertTrue(mustBeOnAddedBlocks instanceof OnAddedBlocks,
                            "First block event must be OnAddedBlocks");
                    Assertions.assertTrue(((OnAddedBlocks) mustBeOnAddedBlocks).blockCount() > 0,
                            "Block count on activate must be non zero");
                },
                () -> {
                    Event mustBeOnActivatedBlocks = blockEventCaptor.getAllValues().get(1);
                    Assertions.assertTrue(mustBeOnActivatedBlocks instanceof OnActivatedBlocks,
                            "First block event must be OnActivatedBlocks");
                    Assertions.assertTrue(((OnActivatedBlocks) mustBeOnActivatedBlocks).blockCount() > 0,
                            "Block count on activate must be non zero");
                });
    }

    @Test
    public void testUnloadChunkAndDeactivationBlock() {
        Vector3i chunkPosition = new Vector3i(0, 0, 0);
        blockAtBlockManager.setLifecycleEventsRequired(true);
        blockAtBlockManager.setEntity(mock(EntityRef.class));

        requestCreatingOrLoadingArea(chunkPosition);
        waitChunkReadyAt(chunkPosition);

        final ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        verify(worldEntity, atLeast(1)).send(eventArgumentCaptor.capture());
        Optional<BeforeChunkUnload> beforeChunkUnload = eventArgumentCaptor.getAllValues()
                .stream()
                .filter((e) -> e instanceof BeforeChunkUnload)
                .map((e) -> (BeforeChunkUnload) e)
                .findFirst();

        Assertions.assertTrue(beforeChunkUnload.isPresent(),
                "World events must have BeforeChunkUnload event when chunk was unload");
        Assertions.assertEquals(beforeChunkUnload.get().getChunkPos(),
                chunkPosition,
                "Chunk position at event not expected");

        //TODO, it is not clear if the activate/addedBlocks event logic is correct.
        //See https://github.com/MovingBlocks/Terasology/issues/3244
        final ArgumentCaptor<Event> blockEventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(blockAtBlockManager.getEntity(), atLeast(1)).send(blockEventCaptor.capture());

        Optional<BeforeDeactivateBlocks> beforeDeactivateBlocks = blockEventCaptor.getAllValues()
                .stream()
                .filter((e) -> e instanceof BeforeDeactivateBlocks)
                .map((e) -> (BeforeDeactivateBlocks) e)
                .findFirst();
        Assertions.assertTrue(beforeDeactivateBlocks.isPresent(),
                "World events must have BeforeDeactivateBlocks event when chunk with lifecycle blocks was unload");
        Assertions.assertTrue(beforeDeactivateBlocks.get().blockCount() > 0,
                "BeforeDeactivateBlocks must have block count more then zero");
    }


    private void waitChunkReadyAt(Vector3i chunkPosition) {
        Assertions.assertTimeoutPreemptively(Duration.of(WAIT_CHUNK_IS_READY_IN_SECONDS, ChronoUnit.SECONDS),
                () -> {
                    while (chunkCache.get(chunkPosition) == null) {}
                    while (!chunkCache.get(chunkPosition).isReady()) {}
                });
    }

}
