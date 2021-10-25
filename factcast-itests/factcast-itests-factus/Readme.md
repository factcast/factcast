Version 3 Compatibility Tests
= 

We want to make sure, that the version 3 client and server stays compatible with the current version 4.

This is achieved as follows:

|Client version | Server version | Tested where |
|---------------|----------------|--------------|
| 3             | 3              | factcast-itests on 0.3.x-branch |
| 4             | 4              | factcast-itests on master branch |
| 4             | 3              | factcast-itests-factus in package `org.factcast.itests.factus.v3compat` |
| 3             | 4              | factcast-itests-factus-compat-clientv3 |

The client v3 tests would usually not be altered, as we do not add new features on v3. In the rare case this happens, `factcast-itests-factus-compat-clientv3` should be updated.

The tests in package `org.factcast.itests.factus.v3compat` derive from the integration tests in that project, only using a different server version. Adding new test cases in existing tests will also automatically test them against the v3 server. 

⚠️ Adding new test classes however will not automatically run them against the v3 server. Simply add a new corresponding test class in package `org.factcast.itests.factus.v3compat`. You need to extend     the original test but configure a different server version:

```java
@FactcastTestConfig(factcastVersion = "0.3.9")
public class FactusClientTestCompatV3 extends FactusClientTest {
}
```