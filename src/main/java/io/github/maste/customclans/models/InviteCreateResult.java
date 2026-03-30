package io.github.maste.customclans.models;

public record InviteCreateResult(Status status) {

    public enum Status {
        CREATED,
        DUPLICATE_FROM_SAME_CLAN
    }
}
