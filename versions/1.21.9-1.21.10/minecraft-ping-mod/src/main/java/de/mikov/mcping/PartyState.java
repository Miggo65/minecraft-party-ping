package de.mikov.mcping;

public class PartyState {
    private String partyCode = "";
    private String serverId = "";

    public synchronized boolean inParty() {
        return !partyCode.isBlank() && !serverId.isBlank();
    }

    public synchronized void joinParty(String partyCode, String serverId) {
        this.partyCode = partyCode;
        this.serverId = serverId;
    }

    public synchronized void leaveParty() {
        this.partyCode = "";
        this.serverId = "";
    }

    public synchronized String partyCode() {
        return partyCode;
    }

    public synchronized String serverId() {
        return serverId;
    }
}
