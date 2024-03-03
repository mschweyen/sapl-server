/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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

package io.sapl.server.ce.ui.views.setup;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import io.sapl.server.ce.model.setup.ApplicationConfigService;
import io.sapl.server.ce.model.setup.SupportedDatasourceTypes;
import io.sapl.server.ce.model.setup.condition.SetupNotFinishedCondition;
import io.sapl.server.ce.ui.utils.ConfirmUtils;
import io.sapl.server.ce.ui.utils.ErrorNotificationUtils;
import io.sapl.server.ce.ui.views.SetupLayout;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@AnonymousAllowed
@RequiredArgsConstructor
@PageTitle("DBMS Setup")
@Route(value = DbmsSetupView.ROUTE, layout = SetupLayout.class)
@Conditional(SetupNotFinishedCondition.class)
public class DbmsSetupView extends VerticalLayout {

    public static final String ROUTE = "/setup/dbms";

    @Autowired
    private transient ApplicationConfigService applicationConfigService;

    private final RadioButtonGroup<String> dbms           = new RadioButtonGroup<>("DBMS");
    private final TextField                dbmsURL        = new TextField("DBMS URL");
    private final TextField                dbmsUsername   = new TextField("DBMS Username");
    private final PasswordField            dbmsPwd        = new PasswordField("DBMS Password");
    private final Button                   dbmsTest       = new Button("Test connection");
    private final Button                   dbmsSaveConfig = new Button("Save DBMS-Configuration");

    private final List<String> dbmsDisplayNames = new ArrayList<>();

    @PostConstruct
    private void init() {
        dbmsDisplayNames.add(SupportedDatasourceTypes.H2.ordinal(), "H2");
        dbmsDisplayNames.add(SupportedDatasourceTypes.MARIADB.ordinal(), "MariaDB");
        add(getLayout());
    }

    public Component getLayout() {
        dbms.setItems(dbmsDisplayNames);
        dbms.setValue(dbmsDisplayNames.get(applicationConfigService.getDbmsConfig().getDbms().ordinal()));
        dbms.addValueChangeListener(e -> {
            updateDbmsConfig();
            applicationConfigService.getDbmsConfig()
                    .setToDbmsDefaults(SupportedDatasourceTypes.values()[dbmsDisplayNames.indexOf(e.getValue())]);
            dbmsURL.setValue(applicationConfigService.getDbmsConfig().getUrl());
        });
        dbmsURL.setValue(applicationConfigService.getDbmsConfig().getUrl());
        dbmsURL.setRequiredIndicatorVisible(true);
        dbmsURL.setClearButtonVisible(true);
        dbmsURL.setValueChangeMode(ValueChangeMode.EAGER);
        dbmsURL.addValueChangeListener(e -> updateDbmsConfig());

        dbmsUsername.setRequiredIndicatorVisible(true);
        dbmsUsername.setClearButtonVisible(true);
        dbmsUsername.setValue(applicationConfigService.getDbmsConfig().getUsername());
        dbmsUsername.setValueChangeMode(ValueChangeMode.EAGER);
        dbmsUsername.addValueChangeListener(e -> updateDbmsConfig());

        dbmsPwd.setRequiredIndicatorVisible(true);
        dbmsPwd.setClearButtonVisible(true);
        dbmsPwd.setValue(applicationConfigService.getDbmsConfig().getPassword());
        dbmsPwd.setValueChangeMode(ValueChangeMode.EAGER);
        dbmsPwd.addValueChangeListener(e -> updateDbmsConfig());
        dbmsTest.setVisible(true);
        dbmsTest.addClickListener(e -> dbmsConnectionTest(false));
        dbmsSaveConfig.setVisible(true);
        dbmsSaveConfig.setEnabled(applicationConfigService.getDbmsConfig().isValidConfig());
        dbmsSaveConfig.addClickListener(e -> writeDbmsConfigToApplicationYml());

        FormLayout dbmsLayout = new FormLayout(dbms, dbmsURL, dbmsUsername, dbmsPwd, dbmsTest, dbmsSaveConfig);
        dbmsLayout.setColspan(dbms, 2);
        dbmsLayout.setColspan(dbmsURL, 2);
        dbmsLayout.setColspan(dbmsSaveConfig, 2);
        dbmsLayout.setColspan(dbmsTest, 2);

        return dbmsLayout;
    }

    private void writeDbmsConfigToApplicationYml() {
        try {
            applicationConfigService.persistDbmsConfig();
            applicationConfigService.getDbmsConfig().setSaved(true);
            ConfirmUtils.inform("saved", "DBMS setup successfully saved");
        } catch (IOException ioe) {
            ConfirmUtils.inform("IO-Error",
                    "Error while writing application.yml-File. Please make sure that the file is not in use and can be written. Otherwise configure the application.yml-file manually. Error: "
                            + ioe.getLocalizedMessage());
        }
    }

    private void dbmsConnectionTest(boolean createDbFileForSupportedDbms) {
        try {
            applicationConfigService.getDbmsConfig().testConnection(createDbFileForSupportedDbms);
            dbmsSaveConfig.setEnabled(applicationConfigService.getDbmsConfig().isValidConfig());
            ConfirmUtils.inform("Success", "Connection test successful");
        } catch (SQLException e) {
            dbmsSaveConfig.setEnabled(false);
            if (applicationConfigService.getDbmsConfig().getDbms() == SupportedDatasourceTypes.H2
                    && e.getErrorCode() == 90146) {
                var dialog = new ConfirmDialog();
                dialog.setHeader("Database does not exist");
                var text = new Span();
                text.getStyle().setWhiteSpace(Style.WhiteSpace.PRE_LINE);
                text.setText(e.getLocalizedMessage() + "\n\nTry now to create it?");
                dialog.setText(text);

                dialog.setCancelable(true);

                dialog.setConfirmText("Try to create");
                dialog.setConfirmButtonTheme("success primary");
                dialog.addConfirmListener(event -> this.dbmsConnectionTest(true));
                dialog.open();

            } else {
                ErrorNotificationUtils.show("Connection to the database not possible. " + e.getLocalizedMessage());
            }

        }
    }

    private void updateDbmsConfig() {
        applicationConfigService.getDbmsConfig()
                .setDbms(SupportedDatasourceTypes.values()[dbmsDisplayNames.indexOf(dbms.getValue())]);
        applicationConfigService.getDbmsConfig().setUrl(dbmsURL.getValue());
        applicationConfigService.getDbmsConfig().setUsername(dbmsUsername.getValue());
        applicationConfigService.getDbmsConfig().setPassword(dbmsPwd.getValue());
        dbmsSaveConfig.setEnabled(applicationConfigService.getDbmsConfig().isValidConfig());
    }

}
