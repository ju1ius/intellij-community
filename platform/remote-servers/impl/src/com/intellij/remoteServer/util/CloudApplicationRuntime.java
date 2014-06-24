/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.remoteServer.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.remoteServer.agent.util.CloudGitAgentDeployment;
import com.intellij.remoteServer.agent.util.CloudLoggingHandler;
import com.intellij.remoteServer.runtime.deployment.DeploymentLogManager;
import com.intellij.remoteServer.runtime.deployment.DeploymentRuntime;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CloudApplicationRuntime extends DeploymentRuntime {

  private final CloudMultiSourceServerRuntimeInstance myServerRuntime;
  private final String myApplicationName;

  private final CloudLoggingHandler myLoggingHandler;
  @Nullable private final DeploymentLogManager myLogManager;

  private final CloudGitAgentDeployment myDeployment;

  public CloudApplicationRuntime(CloudMultiSourceServerRuntimeInstance serverRuntime,
                                 String applicationName,
                                 @Nullable DeploymentLogManager logManager) {
    myServerRuntime = serverRuntime;
    myApplicationName = applicationName;

    myLogManager = logManager;
    myLoggingHandler = logManager == null ? new CloudSilentLoggingHandlerImpl() : new CloudLoggingHandlerImpl(logManager);

    myDeployment = serverRuntime.getAgent().createDeployment(getApplicationName(), myLoggingHandler);
  }

  protected CloudMultiSourceServerRuntimeInstance getServerRuntime() {
    return myServerRuntime;
  }

  public String getApplicationName() {
    return myApplicationName;
  }

  @Nullable
  protected DeploymentLogManager getLogManager() {
    return myLogManager;
  }

  public AgentTaskExecutor getAgentTaskExecutor() {
    return myServerRuntime.getAgentTaskExecutor();
  }

  public CloudLoggingHandler getLoggingHandler() {
    return myLoggingHandler;
  }

  @Override
  public void undeploy(final @NotNull UndeploymentTaskCallback callback) {
    myServerRuntime.getTaskExecutor().submit(new ThrowableRunnable<Exception>() {

      @Override
      public void run() throws Exception {
        try {
          if (!confirmUndeploy()) {
            throw new ServerRuntimeException("Undeploy cancelled");
          }

          undeploy();

          callback.succeeded();
        }
        catch (ServerRuntimeException e) {
          callback.errorOccurred(e.getMessage());
        }
      }
    }, callback);
  }

  public boolean confirmUndeploy() {
    final Ref<Boolean> confirmed = new Ref<Boolean>(false);
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {

      @Override
      public void run() {
        String title = CloudBundle.getText("cloud.undeploy.confirm.title");
        while (true) {
          String password = Messages.showPasswordDialog(CloudBundle.getText("cloud.undeploy.confirm.message", myApplicationName), title);
          if (password == null) {
            return;
          }
          if (password.equals(myServerRuntime.getConfiguration().getPassword())) {
            confirmed.set(true);
            return;
          }
          Messages.showErrorDialog(CloudBundle.getText("cloud.undeploy.confirm.password.incorrect"), title);
        }
      }
    }, ModalityState.defaultModalityState());
    return confirmed.get();
  }

  public CloudGitAgentDeployment getDeployment() {
    return myDeployment;
  }

  public void undeploy() throws ServerRuntimeException {
    getAgentTaskExecutor().execute(new Computable<Object>() {

      @Override
      public Object compute() {
        getDeployment().deleteApplication();
        return null;
      }
    });
  }
}
