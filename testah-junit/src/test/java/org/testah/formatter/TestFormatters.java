/*
#
# Copyright (c) 2014-2016 Cazena, Inc., as an unpublished work.
# This notice does not imply unrestricted or public access to these
# materials which are a trade secret of Cazena, Inc. or its
# subsidiaries or affiliates (together referred to as "Cazena"), and
# which may not be copied, reproduced, used, sold or transferred to any
# third party without Cazena's prior written consent.
#
# All rights reserved.
*/
package org.testah.formatter;

import org.junit.Test;
import org.testah.TS;
import org.testah.framework.annotations.TestCase;
import org.testah.framework.annotations.TestPlan;
import org.testah.framework.testPlan.BrowserTestPlan;

@TestPlan(platforms = "SimpleTestPlatforms", components = "SimpleTestComponents", devices = "SimpleTestDevicess", relatedIds = "TEST_ID", relatedLinks = "HTTP_TEST_LINK", tags = "TEST_TAG", runTypes = "TEST_SVR", name = "SERVICE_TEST", description = "THIS IS A JUST TEST")
public class TestFormatters extends BrowserTestPlan {

	@Test
	@TestCase(description = "THIS IS A JUST TEST")
	public void test1() {
		TS.browser().goTo("http://www.google.com");
		TS.asserts().isFalse(false);
	}

	@Test(expected = AssertionError.class)
	@TestCase(description = "THIS IS A JUST TEST")
	public void test2() {
		TS.browser().goTo("http://www.google.com");
		TS.asserts().isFalse(true);
	}

}
