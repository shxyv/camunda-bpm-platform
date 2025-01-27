/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.camunda.bpm.engine.test.api.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.camunda.bpm.engine.authorization.Authorization.ANY;
import static org.camunda.bpm.engine.authorization.Permissions.TASK_ASSIGN;
import static org.camunda.bpm.engine.authorization.Permissions.UPDATE;
import static org.camunda.bpm.engine.authorization.Permissions.UPDATE_TASK;
import static org.camunda.bpm.engine.authorization.Resources.PROCESS_DEFINITION;
import static org.camunda.bpm.engine.authorization.Resources.TASK;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.camunda.bpm.engine.AuthorizationException;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.util.ClockTestUtil;
import org.camunda.bpm.engine.test.util.EntityRemoveRule;
import org.camunda.bpm.engine.test.util.ObjectProperty;
import org.camunda.bpm.engine.test.util.RemoveAfter;
import org.camunda.bpm.engine.test.util.TriConsumer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

public class SetTaskPropertyAuthorizationTest extends AuthorizationTest {

  protected static final String PROCESS_KEY = "oneTaskProcess";

  @Rule
  public EntityRemoveRule entityRemoveRule = EntityRemoveRule.of(testRule);

  @Parameter(1)
  public String operationName;
  @Parameter(2)
  public TriConsumer<TaskService, String, Object> operation;
  @Parameter(3)
  public String taskId;
  @Parameter(4)
  public Object value;

  protected boolean deleteTask;

  /**
   * Parameters:
   * <p>
   * simpleMethodName: Used for readability (printing first argument as test method instead of a autogenerated consumer name)
   * methodToCall: The method to call during test cases
   * setValue: The value to use to set property to
   * taskQueryBuilderMethodName: The corresponding taskQuery builder method name to use for assertion purposes
   */
  @Parameters(name = "{1} (mode {0})")
  public static List<Object[]> data() {
    TriConsumer<TaskService, String, Object> setPriority = (taskService, taskId, value) -> taskService.setPriority(taskId, (int) value);
    TriConsumer<TaskService, String, Object> setName = (taskService, taskId, value) -> taskService.setName(taskId, (String) value);
    TriConsumer<TaskService, String, Object> setDescription = (taskService, taskId, value) -> taskService.setDescription(taskId, (String) value);
    TriConsumer<TaskService, String, Object> setDueDate = (taskService, taskId, value) -> taskService.setDueDate(taskId, (Date) value);
    TriConsumer<TaskService, String, Object> setFollowUpDate = (taskService, taskId, value) -> taskService.setFollowUpDate(taskId, (Date) value);

    return Arrays.asList(new Object[][] {
        { ProcessEngineConfiguration.AUTHORIZATION_CHECK_REVOKE_ALWAYS, "setPriority", setPriority, "taskId", 80 },
        { ProcessEngineConfiguration.AUTHORIZATION_CHECK_REVOKE_ALWAYS, "setName", setName, "taskId", "name" },
        { ProcessEngineConfiguration.AUTHORIZATION_CHECK_REVOKE_ALWAYS, "setDescription", setDescription, "taskId", "description" },
        { ProcessEngineConfiguration.AUTHORIZATION_CHECK_REVOKE_ALWAYS, "setDueDate", setDueDate, "taskId",  ClockTestUtil.setClockToDateWithoutMilliseconds() },
        { ProcessEngineConfiguration.AUTHORIZATION_CHECK_REVOKE_ALWAYS, "setFollowUpDate", setFollowUpDate, "taskId", ClockTestUtil.setClockToDateWithoutMilliseconds() },
        { ProcessEngineConfiguration.AUTHORIZATION_CHECK_REVOKE_AUTO, "setPriority", setPriority, "taskId", 80 },
        { ProcessEngineConfiguration.AUTHORIZATION_CHECK_REVOKE_AUTO, "setName", setName, "taskId", "name" },
        { ProcessEngineConfiguration.AUTHORIZATION_CHECK_REVOKE_AUTO, "setDescription", setDescription, "taskId", "description" },
        { ProcessEngineConfiguration.AUTHORIZATION_CHECK_REVOKE_AUTO, "setDueDate", setDueDate, "taskId",  ClockTestUtil.setClockToDateWithoutMilliseconds()},
        { ProcessEngineConfiguration.AUTHORIZATION_CHECK_REVOKE_AUTO, "setFollowUpDate", setFollowUpDate, "taskId", ClockTestUtil.setClockToDateWithoutMilliseconds() }
    });
  }

  @Override
  @Before
  public void setUp() throws Exception {
    testRule.deploy("org/camunda/bpm/engine/test/api/oneTaskProcess.bpmn20.xml");
    super.setUp();
  }

  @Test
  @RemoveAfter
  public void shouldSetOperationStandaloneWithoutAuthorization() {
    // given
    createTask(taskId);

    try {
      // when
      operation.accept(taskService, taskId, value);
      fail("Exception expected: It should not be possible to " + operationName);
    } catch (AuthorizationException e) {
      // then
      testRule.assertTextPresent(
          "The user with id '" + userId + "' does not have one of the following permissions: 'TASK_ASSIGN'",
          e.getMessage());
    }
  }

  @Test
  @RemoveAfter
  public void shouldSetOperationStandalone() {
    // given
    createTask(taskId);
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    operation.accept(taskService, taskId, value);

    // then
    Task task = selectSingleTask();

    assertThat(task).isNotNull();
    assertHasPropertyValue(task, operationName, value);
  }

  @Test
  @RemoveAfter
  public void shouldSetOperationStandaloneWithTaskAssignPermission() {
    // given
    createTask(taskId);
    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);

    // when
    operation.accept(taskService, taskId, value);

    // then
    Task task = selectSingleTask();

    assertThat(task).isNotNull();
    assertHasPropertyValue(task, operationName, value);
  }

  @Test
  public void shouldSetOperationOnProcessWithTaskAssignPermissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);

    // when
    operation.accept(taskService, taskId, value);

    // then
    Task task = selectSingleTask();

    assertThat(task).isNotNull();
    assertHasPropertyValue(task, operationName, value);
  }

  @Test
  public void shouldSetOperationOnProcessWithoutAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    try {
      // when
      operation.accept(taskService, taskId, value);
      fail("Exception expected: It should not be possible to " + operationName);
    } catch (AuthorizationException e) {
      // then
      String message = e.getMessage();
      testRule.assertTextPresent(userId, message);
      testRule.assertTextPresent(UPDATE.getName(), message);
      testRule.assertTextPresent(taskId, message);
      testRule.assertTextPresent(TASK.resourceName(), message);
      testRule.assertTextPresent(UPDATE_TASK.getName(), message);
      testRule.assertTextPresent(PROCESS_DEFINITION.resourceName(), message);
    }
  }

  @Test
  public void shouldSetOperationOnProcessWithUpdatePermissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, ANY, userId, UPDATE);

    // when
    operation.accept(taskService, taskId, value);

    // then
    Task task = selectSingleTask();

    assertThat(task).isNotNull();
    assertHasPropertyValue(task, operationName, value);
  }

  @Test
  public void shouldSetOperationOnProcessWithTaskAssignPermissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, ANY, userId, TASK_ASSIGN);

    // when
    operation.accept(taskService, taskId, value);

    // then
    Task task = selectSingleTask();

    assertThat(task).isNotNull();
    assertHasPropertyValue(task, operationName, value);
  }

  @Test
  public void shouldSetOperationOnProcessWithUpdateTasksPermissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    operation.accept(taskService, taskId, value);

    // then
    Task task = selectSingleTask();

    assertThat(task).isNotNull();
    assertHasPropertyValue(task, operationName, value);
  }

  @Test
  public void shouldSetOperationOnProcessWithTaskAssignPermissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, TASK_ASSIGN);

    // when
    operation.accept(taskService, taskId, value);

    // then
    Task task = selectSingleTask();

    assertThat(task).isNotNull();
    assertHasPropertyValue(task, operationName, value);
  }

  @Test
  public void shouldSetOperationOnProcessTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, UPDATE);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    operation.accept(taskService, taskId, value);

    // then
    Task task = selectSingleTask();

    assertThat(task).isNotNull();
    assertHasPropertyValue(task, operationName, value);
  }

  @Test
  public void shouldSetOperationOnProcessWithTaskAssignPermission() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, TASK_ASSIGN);

    // when
    operation.accept(taskService, taskId, value);

    // then
    Task task = selectSingleTask();

    assertThat(task).isNotNull();
    assertHasPropertyValue(task, operationName, value);
  }

  private void assertHasPropertyValue(Task task, String operationName, Object expectedValue) {
    try {
      Object value = ObjectProperty.ofSetterMethod(task, operationName).getValue();

      assertThat(value).isEqualTo(expectedValue);
    } catch (Exception e) {
      fail("Failed to assert property for operationName=" + operationName + " due to : " + e.getMessage());
    }
  }
}