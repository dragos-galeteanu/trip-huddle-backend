package com.triphuddle.api.destination.visa;

/**
 * The strict JSON schema visa guidance is written to.
 */
final class VisaSchemas {

    static final String GUIDANCE = """
            {"type":"object","additionalProperties":false,
             "required":["summary","requirement","maxStay","steps","documents","costs","processingTime",
                         "residencePermitNotes","officialLinks","warnings"],
             "properties":{
               "summary":{"type":"string"},
               "requirement":{"type":"string","enum":["VISA_FREE","VISA_ON_ARRIVAL","ETA","E_VISA",
                              "VISA_REQUIRED","NO_ADMISSION","UNCLEAR"]},
               "maxStay":{"type":"string"},
               "steps":{"type":"array","items":{"type":"string"}},
               "documents":{"type":"array","items":{"type":"string"}},
               "costs":{"type":"string"},
               "processingTime":{"type":"string"},
               "residencePermitNotes":{"type":"string"},
               "officialLinks":{"type":"array","items":{"type":"object","additionalProperties":false,
                   "required":["title","url"],
                   "properties":{"title":{"type":"string"},"url":{"type":"string"}}}},
               "warnings":{"type":"array","items":{"type":"string"}}}}""";

    private VisaSchemas() {
    }
}
