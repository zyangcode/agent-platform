package com.ls.agent.core.team.graph;

import org.bsc.langgraph4j.serializer.StateSerializer;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public final class TeamGraphStateSerializer extends StateSerializer<TeamGraphState> {

    private static final AtomicLong SNAPSHOT_IDS = new AtomicLong();
    private static final ConcurrentMap<Long, Map<String, Object>> SNAPSHOTS = new ConcurrentHashMap<>();

    public TeamGraphStateSerializer() {
        super(TeamGraphState::new);
    }

    @Override
    public void writeData(Map<String, Object> data, ObjectOutput out) throws IOException {
        long snapshotId = SNAPSHOT_IDS.incrementAndGet();
        SNAPSHOTS.put(snapshotId, new LinkedHashMap<>(data));
        out.writeLong(snapshotId);
    }

    @Override
    public Map<String, Object> readData(ObjectInput in) throws IOException {
        long snapshotId = in.readLong();
        Map<String, Object> data = SNAPSHOTS.remove(snapshotId);
        if (data == null) {
            throw new IOException("Team graph state snapshot is not available");
        }
        return new LinkedHashMap<>(data);
    }
}
