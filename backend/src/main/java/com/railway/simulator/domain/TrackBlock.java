package com.railway.simulator.domain;

/**
 * TrackBlock represents a discrete physical or platform section of the simulated railway corridor.
 * Derived directly from CorridorGraph topological connections.
 *
 * NOTE: Educational simulation model only.
 */
public class TrackBlock {

    private String blockId;
    private String fromStationCode;
    private String toStationCode;
    private LineType lineType;
    private BlockType blockType = BlockType.INTER_STATION;
    private boolean occupied = false;
    private Long occupyingTrainId;
    private String occupyingTrainNumber;
    private String direction; // "UP" (Southbound / Mumbai Central), "DOWN" (Northbound / Virar), "COMMON"
    private boolean closed = false;
    private double lengthKm = 2.5;
    private String nextBlockId;

    public TrackBlock() {}

    public TrackBlock(String blockId, String fromStationCode, String toStationCode,
                      LineType lineType, BlockType blockType, String direction, double lengthKm) {
        this.blockId = blockId;
        this.fromStationCode = fromStationCode;
        this.toStationCode = toStationCode;
        this.lineType = lineType;
        this.blockType = blockType;
        this.direction = direction;
        this.lengthKm = lengthKm;
        this.occupied = false;
        this.closed = false;
    }

    public void occupy(Long trainId, String trainNumber) {
        this.occupied = true;
        this.occupyingTrainId = trainId;
        this.occupyingTrainNumber = trainNumber;
    }

    public void vacate() {
        this.occupied = false;
        this.occupyingTrainId = null;
        this.occupyingTrainNumber = null;
    }

    public String getBlockId() {
        return blockId;
    }

    public void setBlockId(String blockId) {
        this.blockId = blockId;
    }

    public String getFromStationCode() {
        return fromStationCode;
    }

    public void setFromStationCode(String fromStationCode) {
        this.fromStationCode = fromStationCode;
    }

    public String getToStationCode() {
        return toStationCode;
    }

    public void setToStationCode(String toStationCode) {
        this.toStationCode = toStationCode;
    }

    public LineType getLineType() {
        return lineType;
    }

    public void setLineType(LineType lineType) {
        this.lineType = lineType;
    }

    public BlockType getBlockType() {
        return blockType;
    }

    public void setBlockType(BlockType blockType) {
        this.blockType = blockType;
    }

    public boolean isOccupied() {
        return occupied;
    }

    public void setOccupied(boolean occupied) {
        this.occupied = occupied;
    }

    public Long getOccupyingTrainId() {
        return occupyingTrainId;
    }

    public void setOccupyingTrainId(Long occupyingTrainId) {
        this.occupyingTrainId = occupyingTrainId;
    }

    public String getOccupyingTrainNumber() {
        return occupyingTrainNumber;
    }

    public void setOccupyingTrainNumber(String occupyingTrainNumber) {
        this.occupyingTrainNumber = occupyingTrainNumber;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public double getLengthKm() {
        return lengthKm;
    }

    public void setLengthKm(double lengthKm) {
        this.lengthKm = lengthKm;
    }

    public String getNextBlockId() {
        return nextBlockId;
    }

    public void setNextBlockId(String nextBlockId) {
        this.nextBlockId = nextBlockId;
    }
}
