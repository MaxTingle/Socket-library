package com.maxtingle;

import uk.co.maxtingle.Main;

public class Message
{
    public String message;

    public Message(String message) {
        this.message = message;
    }

    public String toString() {
        return Main.jsonParser.toJson(this);
    }

    public static Message fromJson(String json) {
        return Main.jsonParser.fromJson(json, Message.class);
    }
}
