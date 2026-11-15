package com.triphuddle.api.destination.trip;

/**
 * The strict JSON schemas of an itinerary and of one day, as the openai worker asks for them.
 */
final class ItinerarySchemas {

    static final String ITEM = """
            {"type":"object","properties":{
              "timeOfDay":{"type":"string","enum":["MORNING","AFTERNOON","EVENING"]},
              "title":{"type":"string"},"description":{"type":"string"},"placeId":{"type":"string"},
              "durationMinutes":{"type":"integer"},"tips":{"type":"string"}},
             "required":["timeOfDay","title","description","placeId","durationMinutes","tips"],
             "additionalProperties":false}""";

    static final String DAY = """
            {"type":"object","properties":{
              "dayNumber":{"type":"integer"},"title":{"type":"string"},"summary":{"type":"string"},
              "items":{"type":"array","items":%s}},
             "required":["dayNumber","title","summary","items"],"additionalProperties":false}""".formatted(ITEM);

    static final String ITINERARY = """
            {"type":"object","properties":{
              "summary":{"type":"string"},"days":{"type":"array","items":%s}},
             "required":["summary","days"],"additionalProperties":false}""".formatted(DAY);

    private ItinerarySchemas() {
    }
}
