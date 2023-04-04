package org.springframework.cloud;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.springframework.cloud.app.ApplicationInstanceInfo;
import org.springframework.cloud.service.BaseServiceInfo;
import org.springframework.cloud.service.ServiceInfo;

/**
 * Test for AbstractCloudConnector.
 * 
 * @author Ramnivas Laddad
 *
 */
public class AbstractCloudConnectorTest {
	
	@Test 
	public void fallbacksCorrectly() {
		TestServiceData acceptableServiceData = new TestServiceData("my-service1", "test-tag");
		TestServiceData unAcceptableServiceData = new TestServiceData("my-service2", "unknown-tag");
		
		CloudConnector testCloudConnector = new TestCloudConnector(acceptableServiceData, unAcceptableServiceData);
		List<ServiceInfo> serviceInfos = testCloudConnector.getServiceInfos();
		assertNotNull(serviceInfos);
		assertEquals(2, serviceInfos.size());
		assertThat(serviceInfos.get(0), is(instanceOf(TestServiceInfo.class)));
		assertThat(serviceInfos.get(0), is(instanceOf(BaseServiceInfo.class)));
	}
}

class TestCloudConnector extends AbstractCloudConnector<TestServiceData> {
	private final List<TestServiceData> serviceDatas = new ArrayList<>();

	public TestCloudConnector(TestServiceData... serviceDatas) {
		super(TestServiceInfoCreator.class);
		
		for (TestServiceData serviceData : serviceDatas) {
			this.serviceDatas.add(serviceData);
		}
	}

	@Override
	public boolean isInMatchingCloud() {
		return true;
	}

	@Override
	public ApplicationInstanceInfo getApplicationInstanceInfo() {
		return null;
	}

	@Override
	protected List<TestServiceData> getServicesData() {
		return serviceDatas;
	}

	@Override
	protected FallbackServiceInfoCreator<BaseServiceInfo,TestServiceData> getFallbackServiceInfoCreator() {
		return new TestFallbackServiceInfoCreator();
	}
}

class TestFallbackServiceInfoCreator extends FallbackServiceInfoCreator<BaseServiceInfo,TestServiceData> {
	@Override
	public BaseServiceInfo createServiceInfo(TestServiceData serviceData) {
		return new BaseServiceInfo(serviceData.getId());
	}
}

class TestServiceInfo extends BaseServiceInfo {
	private final String tag;

	public TestServiceInfo(String id, String tag) {
		super(id);
		this.tag = tag;
	}
	
	public String getTag() {
		return this.tag;
	}
}

class TestServiceData {
	private final String id;
	private final String tag;

	public TestServiceData(String id, String tag) {
		this.id = id;
		this.tag = tag;
	}

	public String getId() {
		return id;
	}

	public String getTag() {
		return tag;
	}
}

class TestCompositeServiceData extends TestServiceData {

	private final TestServiceData[] constituents;

    public TestCompositeServiceData(String id, String tag, TestServiceData... constituents) {
        super(id, tag);
        this.constituents = constituents;
    }

    public TestServiceData[] getConstituents() {
        return constituents;
    }
    
}