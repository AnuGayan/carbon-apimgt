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

import React from 'react';
import classNames from 'classnames';
import PropTypes from 'prop-types';
import { makeStyles } from '@material-ui/core/styles';
import { AccordionDetails, AccordionSummary } from '@material-ui/core';
import ExpandMoreIcon from '@material-ui/icons/ExpandMore';
import Typography from '@material-ui/core/Typography';
import WarningOutlined from '@material-ui/icons/WarningOutlined';
import Grid from '@material-ui/core/Grid';
import Paper from '@material-ui/core/Paper';
import Accordion from '@material-ui/core/Accordion';
import ErrorOutlineOutlinedIcon from '@material-ui/icons/ErrorOutlineOutlined';

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
    errorContentGrid: {
        padding: '10px',
        backgroundColor: theme.palette.background.default,
    },
    errorGrid: {
        margin: theme.spacing(1),
    },
    warningPaper: {
        backgroundColor: theme.palette.warning.light,
        alignItems: 'center',
        height: theme.spacing(5),
        display: 'flex',
        paddingLeft: '10px',
        paddingRight: '10px',
    },
    errorPaper: {
        backgroundColor: theme.palette.error.dark,
        color: theme.palette.error.contrastText,
        alignItems: 'center',
        height: theme.spacing(5),
        display: 'flex',
        paddingLeft: '10px',
        paddingRight: '10px',
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
                            expandIcon={<ExpandMoreIcon />}
                            aria-controls='panel1a-content'
                            id='panel1a-header'
                        >
                            <Typography>
                                <WarningOutlined
                                    style={{ float: 'left', marginRight: 4 }}
                                />
                                {' Found '}
                                {noOfErrors}
                                {' warnings while parsing the file'}
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
                                    <Grid item xs={12} className={classes.errorContentGrid}>
                                        {errorDetails.errors.map((error) => {
                                            return (
                                                <Grid item xs={12} className={classes.errorGrid}>
                                                    <Paper elevation={2} className={classes.warningPaper}>
                                                        {
                                                            error.description.charAt(0).toUpperCase()
                                                            + error.description.slice(1)
                                                        }
                                                    </Paper>
                                                </Grid>
                                            );
                                        })}
                                    </Grid>
                                </Paper>
                            </Grid>
                        </AccordionDetails>
                    </Accordion>
                )}
            {isValid.file && (
                <Accordion className={classes.errorAccordion}>
                    <AccordionSummary
                        expandIcon={<ExpandMoreIcon />}
                        aria-controls='panel1a-content'
                    >
                        <Typography>
                            <ErrorOutlineOutlinedIcon
                                style={{ float: 'left', marginRight: 4 }}
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
                                <Grid item xs={12} className={classes.errorContentGrid}>
                                    {errorDetails.errors.map((error) => {
                                        return (
                                            <Grid item xs={12} className={classes.errorGrid}>
                                                <Paper elevation={2} className={classes.errorPaper}>
                                                    {
                                                        error.description.charAt(0).toUpperCase()
                                                        + error.description.slice(1)
                                                    }
                                                </Paper>
                                            </Grid>
                                        );
                                    })}
                                </Grid>
                            </Paper>
                        </Grid>
                    </AccordionDetails>
                </Accordion>
            )}
        </>
    );
}

ErrorAccordion.propTypes = {
    errorDetails: PropTypes.objectOf(PropTypes.object).isRequired,
    noOfErrors: PropTypes.number.isRequired,
    isValid: PropTypes.objectOf(PropTypes.object).isRequired,
};
