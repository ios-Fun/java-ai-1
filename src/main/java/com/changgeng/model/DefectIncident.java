package com.changgeng.model;

import lombok.Data;

import java.util.Date;

@Data
public class DefectIncident {
    Integer defectincidentid;
    Integer thedefectinstanceid;
    Integer theparentinstanceid;
    Integer theunitinstanceid;
    Integer thepowerstationid;
    Integer thecomponentoverhaulitemid;
    Integer serialnumber;
    Boolean havesendoverhaul;
    Boolean closed;
    Integer type;
    Integer thediagnosistypeid;
    Integer previousflownodeid;
    Integer currentflownodeid;
    String currentstatusname;
    String currentstatuscomment;
    Date firstoccureddatetime;
    Date createddatetime;
    Date lastoccureddatetime;
    Date lastprocessdatetime;
    Date expireddatetime;
    String defectName;
    String severityLevel;
}
