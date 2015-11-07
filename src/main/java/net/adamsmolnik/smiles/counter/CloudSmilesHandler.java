package net.adamsmolnik.smiles.counter;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import javax.json.Json;
import javax.json.JsonObject;

import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;

/**
 * @author asmolnik
 *
 */
public class CloudSmilesHandler {

	public void handle(InputStream is, OutputStream os, Context context) {
		String now = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME);
		JsonObject json = Json.createReader(is).readObject();
		String sn = json.getString("serialNumber");
		String bv = json.getString("batteryVoltage");
		String ct = json.getString("clickType");
		String clientId = json.containsKey("clientId") ? json.getString("clientId") : "undefined";

		AWSSecurityTokenServiceClient stsClient = new AWSSecurityTokenServiceClient();
		AssumeRoleRequest assumeReq = new AssumeRoleRequest().withRoleArn("arn:aws:iam::542175458111:role/cloudyna2015-cloud-smiles-write-access")
				.withDurationSeconds(3600).withRoleSessionName("lambda");
		AssumeRoleResult res = stsClient.assumeRole(assumeReq);
		BasicSessionCredentials bsc = new BasicSessionCredentials(res.getCredentials().getAccessKeyId(), res.getCredentials().getSecretAccessKey(),
				res.getCredentials().getSessionToken());
		DynamoDB db = new DynamoDB(new AmazonDynamoDBClient(bsc));
		String thingId = map(sn, db);

		db.getTable("cloudyna2015-cloud-smiles").putItem(new Item().withString("thingId", thingId).withString("serialNumber", sn)
				.withString("clientId", clientId).withString("eventDateTime", now).withString("clickType", ct).withString("batteryVoltage", bv));
	}

	private String map(String sn, DynamoDB db) {
		Table table = db.getTable("things-mapper");
		Item item = table.getItem("serialNumber", sn);
		if (item == null) {
			String mapResult = "undefined-" + UUID.randomUUID().toString();
			table.putItem(new Item().withString("serialNumber", sn).withString("thingId", mapResult));
			return mapResult;
		}
		return item.getString("thingId");
	}

}
