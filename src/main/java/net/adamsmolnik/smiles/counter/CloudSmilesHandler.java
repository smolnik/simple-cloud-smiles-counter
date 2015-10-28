package net.adamsmolnik.smiles.counter;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import javax.json.Json;
import javax.json.JsonObject;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.lambda.runtime.Context;

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

		PutItemRequest req = new PutItemRequest().withTableName("cloud-smiles").addItemEntry("serialNumber", new AttributeValue(sn))
				.addItemEntry("eventDateTime", new AttributeValue(now)).addItemEntry("clickType", new AttributeValue(ct))
				.addItemEntry("batteryVoltage", new AttributeValue(bv));
		new AmazonDynamoDBClient().putItem(req);
	}

}
