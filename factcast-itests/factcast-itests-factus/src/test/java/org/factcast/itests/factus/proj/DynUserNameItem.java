package org.factcast.itests.factus.proj;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import org.factcast.itests.factus.DynamoBatchingITest;

@DynamoDBTable(tableName = DynamoBatchingITest.TABLE_NAME)
public class DynUserNameItem {

  String id;
  String name;
}
