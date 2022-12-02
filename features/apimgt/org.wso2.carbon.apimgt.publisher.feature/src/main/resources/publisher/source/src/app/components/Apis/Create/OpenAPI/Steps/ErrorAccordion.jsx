/*
 * Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import React, {Component, useState} from 'react';
import classNames from 'classnames';
import ResourceNotFound from 'AppComponents/Base/Errors/ResourceNotFound';
import PropTypes from 'prop-types';
import {makeStyles} from '@material-ui/core/styles';
import {AccordionDetails, AccordionSummary} from "@material-ui/core";
import ExpandMoreIcon from "@material-ui/icons/ExpandMore";
import Typography from "@material-ui/core/Typography";
import WarningOutlined from "@material-ui/icons/WarningOutlined";
import Grid from "@material-ui/core/Grid";
import Paper from "@material-ui/core/Paper";
import ErrorTableHead from "AppComponents/Apis/Create/OpenAPI/Steps/ErrorTableHead";
import ErrorTableContent from "AppComponents/Apis/Create/OpenAPI/Steps/ErrorTableContent";
import Accordion from "@material-ui/core/Accordion";
import ErrorOutlineOutlinedIcon from "@material-ui/icons/ErrorOutlineOutlined";
import Table from "@material-ui/core/Table";

/**
 * @inheritdoc
 * @param {*} theme theme object
 */
const useStyles = makeStyles((theme) => ({
    fullHeight: {
        height: '100%',
    },
    tableRow: {
        height: theme.spacing(5),
        '& td': {
            padding: theme.spacing(0.5),
        },
    },
    appOwner: {
        pointerEvents: 'none',
    },
    appName: {
        '& a': {
            color: '#1b9ec7 !important',
        },
    },
    errorAccordion: {
        backgroundColor: theme.palette.error.dark,
        color: theme.palette.error.contrastText,
        boxShadow: 'none',
    },
    warningAccordion: {
        backgroundColor: theme.palette.warning.light,
        color: theme.palette.warning.contrastText,
        boxShadow: 'none',
    },
    errorTablePaper: {
        '& table tr td': {
            paddingLeft: theme.spacing(1),
        },
        '& table tr td:first-child, & table tr th:first-child': {
            paddingLeft: theme.spacing(2),
        },
        '& table th': {
            backgroundColor: theme.custom.listView.tableHeadBackground,
            color: theme.palette.getContrastText(theme.custom.listView.tableHeadBackground),
            paddingLeft: theme.spacing(1),
        },
        '& table tr td button.Mui-disabled span.material-icons': {
            color: theme.palette.action.disabled,
        },
    },
}));

/**
 *
 *
 * @class ErrorAccordion
 * @extends {Component}
 */
export default function ErrorAccordion(props) {

    const { errorDetails, noOfErrors, isValid } = props;
    const classes = useStyles();


    return (
        <>
            {errorDetails.isValid && noOfErrors > 0
                && (
                    <Accordion className={classes.warningAccordion}>
                        <AccordionSummary
                            expandIcon={<ExpandMoreIcon/>}
                            aria-controls='panel1a-content'
                            id='panel1a-header'
                        >
                            <Typography>
                                <WarningOutlined
                                    style={{float: 'left', marginRight: 4}}
                                />
                                {' Found '}
                                {noOfErrors}
                                {' errors while parsing the file'}
                            </Typography>
                        </AccordionSummary>
                        <AccordionDetails>
                            <Grid item xs={12}>
                                <Paper className={classNames(
                                    classes.errorTablePaper,
                                    classes.warningAccordion,
                                    classes.appTablePaperPosition,
                                )}
                                >
                                    <Table>
                                        <ErrorTableContent
                                            errors={errorDetails}
                                            isValid={isValid}
                                        />
                                    </Table>
                                </Paper>
                            </Grid>
                        </AccordionDetails>
                    </Accordion>
                )}
            {isValid.file && (
                <Accordion className={classes.errorAccordion}>
                    <AccordionSummary
                        expandIcon={<ExpandMoreIcon/>}
                        aria-controls='panel1a-content'
                    >
                        <Typography>
                            <ErrorOutlineOutlinedIcon
                                style={{float: 'left', marginRight: 4}}
                            />
                            {' Found '}
                            {noOfErrors}
                            {' errors while parsing the file'}
                        </Typography>
                    </AccordionSummary>
                    <AccordionDetails>
                        <Grid item xs={12}>
                            <Paper
                                className={classNames(
                                    classes.errorTablePaper,
                                    classes.errorAccordion,
                                    classes.appTablePaperPosition,
                                )}
                                elevation={3}
                            >
                                <Table>
                                <ErrorTableContent
                                    errors={errorDetails}
                                    isValid={isValid}
                                />
                                </Table>
                            </Paper>
                        </Grid>
                    </AccordionDetails>
                </Accordion>
            )}
        </>
    );
}

ErrorAccordion.propTypes = {
    errorDetails: PropTypes.object.isRequired,
    noOfErrors: PropTypes.number.isRequired,
    isValid: PropTypes.object
};