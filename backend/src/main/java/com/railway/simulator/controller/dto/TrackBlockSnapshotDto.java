package com.railway.simulator.controller.dto;

import com.railway.simulator.domain.BlockType;
import com.railway.simulator.domain.LineType;

public class TrackBlockSnapshotDto {
    private String blockId;
    private String fromStationCode;
    private String toStationCode;
    private LineType lineType;
    private BlockType blockType;
    private boolean occupied;
    private Long occupyingTrainId;
    private String occupyingTrainNumber;
    private boolean closed;
    private String direction;
    private double lengthKm;
    private String nextBlockId;

    public TrackBlockSnapshotDto() {}

    public String getBlockId() { return blockId; }
    public void setBlockId(String blockId) { this.blockId = blockId; }

    public String getFromStationCode() { return fromStationCode; }
    public void setFromStationCode(String fromStationCode) { this.fromStationCode = fromStationCode; }

    public String getToStationCode() { return toStationCode; }
    public void setToStationCode(String toStationCode) { this.toStationCode = toStationCode; }

    public LineType getLineType() { return lineType; }
    public void setLineType(LineType lineType) { this.lineType = lineType; }

    public BlockType getBlockType() { return blockType; }
    public void setBlockType(BlockType blockType) { this.blockType = blockType; }

    public boolean isOccupied() { return occupied; }
    public void setOccupied(boolean occupied) { this.occupied = occupied; }

    public Long getOccupyingTrainId() { return occupyingTrainId; }
    public void setOccupyingTrainId(Long occupyingTrainId) { this.occupyingTrainId = occupyingTrainId; }

    public String getOccupyingTrainNumber() { return occupyingTrainNumber; }
    public void setOccupyingTrainNumber(String occupyingTrainNumber) { this.occupyingTrainNumber = occupyingTrainNumber; }

    public boolean isClosed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public double getLengthKm() { return lengthKm; }
    public void setLengthKm(double lengthKm) { this.lengthKm = lengthKm; }

    public String getNextBlockId() { return nextBlockId; }
    public void setNextBlockId(String nextBlockId) { this.nextBlockId = nextBlockId; }
}
