/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
import React, { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import Radio from '@material-ui/core/Radio';
import Grid from '@material-ui/core/Grid';
import TextField from '@material-ui/core/TextField';
import RadioGroup from '@material-ui/core/RadioGroup';
import FormControlLabel from '@material-ui/core/FormControlLabel';
import FormControl from '@material-ui/core/FormControl';
import FormLabel from '@material-ui/core/FormLabel';
import { makeStyles } from '@material-ui/core/styles';
import { FormattedMessage } from 'react-intl';
import CircularProgress from '@material-ui/core/CircularProgress';
import InputAdornment from '@material-ui/core/InputAdornment';
import CheckIcon from '@material-ui/icons/Check';
import ErrorOutlineIcon from '@material-ui/icons/ErrorOutline';
import Button from '@material-ui/core/Button';
import List from '@material-ui/core/List';
import ListItem from '@material-ui/core/ListItem';
import ListItemAvatar from '@material-ui/core/ListItemAvatar';
import Avatar from '@material-ui/core/Avatar';
import IconButton from '@material-ui/core/IconButton';
import InsertDriveFile from '@material-ui/icons/InsertDriveFile';
import DeleteIcon from '@material-ui/icons/Delete';
import ListItemSecondaryAction from '@material-ui/core/ListItemSecondaryAction';
import ListItemText from '@material-ui/core/ListItemText';

import APIValidation from 'AppData/APIValidation';
import API from 'AppData/api';
import DropZoneLocal, { humanFileSize } from 'AppComponents/Shared/DropZoneLocal';
import { AccordionDetails, AccordionSummary } from '@material-ui/core';
import Accordion from '@material-ui/core/Accordion';
import ExpandMoreIcon from '@material-ui/icons/ExpandMore';
import ErrorOutlineOutlinedIcon from '@material-ui/icons/ErrorOutlineOutlined';
import WarningOutlined from '@material-ui/icons/WarningOutlined';
import Typography from '@material-ui/core/Typography';
import Paper from '@material-ui/core/Paper';
import ErrorTableHead from 'AppComponents/Apis/Create/OpenAPI/Steps/ErrorTableHead';
import ErrorTableContent from 'AppComponents/Apis/Create/OpenAPI/Steps/ErrorTableContent';
import classNames from 'classnames';


const useStyles = makeStyles((theme) => ({
    mandatoryStar: {
        color: theme.palette.error.main,
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
    appTablePaperPosition: {
        width: '100%',
        display: 'inline-table',
    },
    appErrorPaperPosition: {
        width: '90%',
        display: 'inline-table',
    },
    errorTablePaper: {
        '& table tr td': {
            paddingLeft: theme.spacing(1),
        },
        '& table tr td:first-child, & table tr th:first-child': {
            paddingLeft: theme.spacing(2),
        },
        '& table tr:nth-child(even)': {
            backgroundColor: theme.custom.listView.tableBodyEvenBackgrund,
            '& td, & a, & .material-icons': {
                color: theme.palette.getContrastText(theme.custom.listView.tableBodyEvenBackgrund),
            },
        },
        '& table tr:nth-child(odd)': {
            backgroundColor: theme.custom.listView.tableBodyOddBackgrund,
            '& td, & a, & .material-icons': {
                color: theme.palette.getContrastText(theme.custom.listView.tableBodyOddBackgrund),
            },
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
    tableRow: {
        height: theme.spacing(5),
        '& td': {
            padding: theme.spacing(0.5),
        },
    },
}));

/**
 * Sub component of API Create using OpenAPI UI, This is handling the taking input of WSDL file or URL from the user
 * In the create API using OpenAPI wizard first step out of 2 steps
 * @export
 * @param {*} props
 * @returns {React.Component} @inheritdoc
 */
export default function ProvideOpenAPI(props) {
    const { apiInputs, inputsDispatcher, onValidate } = props;
    const isFileInput = apiInputs.inputType === 'file';
    const { inputType, inputValue } = apiInputs;
    const classes = useStyles();
    // If valid value is `null`,that means valid, else an error object will be there
    const [isValid, setValidity] = useState({});
    const [isValidating, setIsValidating] = useState(false);
    const [errorDetails, setErrorDetails] = useState({ isValid: false, errors: [] });
    const [noOfErrors, setNoOfErrors] = useState(0);

    /**
     *
     *
     * @param {*} files
     */
    function onDrop(files) {
        setIsValidating(true);

        // Why `files.pop()` below is , We only handle one OpenAPI file at a time,
        // So if use provide multiple, We would only
        // accept the first file. This information is shown in the dropdown helper text
        const file = files.pop();
        let validFile = null;
        API.validateOpenAPIByFile(file)
            .then((response) => {
                const {
                    body: { isValid: isValidFile, info, errors },
                } = response;
                if (isValidFile) {
                    validFile = file;
                    inputsDispatcher({ action: 'preSetAPI', value: info });
                    setValidity({ ...isValid, file: null });
                    setErrorDetails({ isValid: response.body.isValid, errors });
                    setNoOfErrors(response.body.errors.length);
                } else {
                    setValidity({ ...isValid, file: { message: 'OpenAPI content validation failed!' } });
                    setErrorDetails({ isValid: response.body.isValid, errors });
                    setNoOfErrors(response.body.errors.length);
                }
            })
            .catch((error) => {
                setValidity({ ...isValid, file: { message: 'OpenAPI content validation failed!' } });
                console.error(error);
            })
            .finally(() => {
                setIsValidating(false); // Stop the loading animation
                onValidate(validFile !== null); // If there is a valid file then validation has passed
                // If the given file is valid , we set it as the inputValue else set `null`
                inputsDispatcher({ action: 'inputValue', value: validFile });
            });
    }

    /**
     * Trigger the provided onValidate call back on each input validation run
     * Do the validation state aggregation and call the onValidate method with aggregated value
     * @param {Object} state Validation state object returned from Joi `.validate()` method
     */
    function validateURL(value) {
        const state = APIValidation.url.required().validate(value).error;
        // State `null` means URL is valid, We do backend validation only in valid URLs
        if (state === null) {
            setIsValidating(true);
            API.validateOpenAPIByUrl(apiInputs.inputValue, { returnContent: true }).then((response) => {
                const {
                    body: { isValid: isValidURL, info, content },
                } = response;
                if (isValidURL) {
                    info.content = content;
                    inputsDispatcher({ action: 'preSetAPI', value: info });
                    setValidity({ ...isValid, url: null });
                } else {
                    setValidity({ ...isValid, url: { message: 'OpenAPI content validation failed!' } });
                }
                onValidate(isValidURL);
                setIsValidating(false);
            }).catch((error) => {
                setValidity({ url: { message: error.message } });
                onValidate(false);
                setIsValidating(false);
                console.error(error);
            });
            // Valid URL string
            // TODO: Handle catch network or api call failures ~tmkb
        } else {
            setValidity({ ...isValid, url: state });
            onValidate(false);
        }
    }

    useEffect(() => {
        if (inputValue) {
            if (inputType === ProvideOpenAPI.INPUT_TYPES.FILE) {
                onDrop([inputValue]);
            } else if (inputType === ProvideOpenAPI.INPUT_TYPES.URL) {
                validateURL(inputValue);
            }
        }
    }, [inputType, inputValue]);

    // TODO: Use validation + input to separate component that can be share with wsdl,swagger,graphql URL inputs ~tmkb
    const isInvalidURL = Boolean(isValid.url);
    let urlStateEndAdornment = null;
    if (isValidating) {
        urlStateEndAdornment = (
            <InputAdornment position='end'>
                <CircularProgress />
            </InputAdornment>
        );
    } else if (isValid.url !== undefined) {
        if (isInvalidURL) {
            urlStateEndAdornment = (
                <InputAdornment position='end'>
                    <ErrorOutlineIcon fontSize='large' color='error' />
                </InputAdornment>
            );
        } else {
            urlStateEndAdornment = (
                <InputAdornment position='end' id='url-validated'>
                    <CheckIcon fontSize='large' color='primary' />
                </InputAdornment>
            );
        }
    }

    return (
        <>
            <Grid container spacing={5}>
                <Grid item xs={12} md={12}>
                    <FormControl component='fieldset'>
                        <FormLabel component='legend'>
                            <>
                                <sup className={classes.mandatoryStar}>*</sup>
                                {' '}
                                <FormattedMessage
                                    id='Apis.Create.OpenAPI.Steps.ProvideOpenAPI.Input.type'
                                    defaultMessage='Input Type'
                                />
                            </>
                        </FormLabel>
                        <RadioGroup
                            aria-label='Input type'
                            value={apiInputs.inputType}
                            onChange={(event) => inputsDispatcher({ action: 'inputType', value: event.target.value })}
                        >
                            <FormControlLabel
                                value={ProvideOpenAPI.INPUT_TYPES.URL}
                                control={<Radio color='primary' />}
                                label='OpenAPI URL'
                                id='open-api-url-select-radio'
                            />
                            <FormControlLabel
                                value={ProvideOpenAPI.INPUT_TYPES.FILE}
                                control={<Radio color='primary' />}
                                label='OpenAPI File/Archive'
                                aria-label='OpenAPI File/Archive'
                                id='open-api-file-select-radio'
                            />
                        </RadioGroup>
                    </FormControl>
                </Grid>
                <Grid item xs={12}>
                    <Paper className={classes.appErrorPaperPosition} elevation={3}>
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
                                                <ErrorTableHead
                                                    errorTableHeader={false}
                                                />
                                                <ErrorTableContent
                                                    errors={errorDetails}
                                                    isValid={isValid}
                                                />
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
                                            <ErrorTableHead
                                                errorTableHeader
                                            />
                                            <ErrorTableContent
                                                errors={errorDetails}
                                                isValid={isValid}
                                            />
                                        </Paper>
                                    </Grid>
                                </AccordionDetails>
                            </Accordion>
                        )}
                    </Paper>
                </Grid>
                <Grid item xs={10} md={11}>
                    {isFileInput ? (
                        <>
                            {apiInputs.inputValue ? (
                                <List>
                                    <ListItem key={apiInputs.inputValue.path}>
                                        <ListItemAvatar>
                                            <Avatar>
                                                <InsertDriveFile />
                                            </Avatar>
                                        </ListItemAvatar>
                                        <ListItemText
                                            primary={`${apiInputs.inputValue.path} -
                                    ${humanFileSize(apiInputs.inputValue.size)}`}
                                        />
                                        <ListItemSecondaryAction>
                                            <IconButton
                                                edge='end'
                                                aria-label='delete'
                                                onClick={() => {
                                                    inputsDispatcher({ action: 'inputValue', value: null });
                                                    inputsDispatcher({ action: 'isFormValid', value: false });
                                                }}
                                            >
                                                <DeleteIcon />
                                            </IconButton>
                                        </ListItemSecondaryAction>
                                    </ListItem>
                                </List>
                            ) : (
                                <DropZoneLocal
                                    error={isValid.file}
                                    onDrop={onDrop}
                                    files={apiInputs.inputValue}
                                    accept='.bz,.bz2,.gz,.rar,.tar,.zip,.7z,.json,application/json,.yaml'
                                >
                                    {isValidating ? (<CircularProgress />)
                                        : ([
                                            <FormattedMessage
                                                id='Apis.Create.OpenAPI.Steps.ProvideOpenAPI.Input.file.dropzone'
                                                defaultMessage={'Drag & Drop Open API File/Archive '
                                                 + 'here {break} or {break} Browse files'}
                                                values={{ break: <br /> }}
                                            />,
                                            <Button
                                                color='primary'
                                                variant='contained'
                                                id='browse-to-upload-btn'
                                            >
                                                <FormattedMessage
                                                    id='Apis.Create.OpenAPI.Steps.ProvideOpenAPI.Input.file.upload'
                                                    defaultMessage='Browse File to Upload'
                                                />
                                            </Button>,
                                        ]
                                        )}
                                </DropZoneLocal>
                            )}
                        </>
                    ) : (
                        <TextField
                            autoFocus
                            id='outlined-full-width'
                            label='OpenAPI URL'
                            placeholder='Enter OpenAPI URL'
                            fullWidth
                            margin='normal'
                            variant='outlined'
                            onChange={({ target: { value } }) => inputsDispatcher({ action: 'inputValue', value })}
                            value={apiInputs.inputValue}
                            InputLabelProps={{
                                shrink: true,
                            }}
                            InputProps={{
                                onBlur: ({ target: { value } }) => {
                                    validateURL(value);
                                },
                                endAdornment: urlStateEndAdornment,
                            }}
                            // 'Give the URL of OpenAPI endpoint'
                            helperText={(isValid.url && isValid.url.message) || 'Click away to validate the URL'}
                            error={isInvalidURL}
                            data-testid='swagger-url-endpoint'
                        />
                    )}
                </Grid>
                <Grid item xs={2} md={5} />
            </Grid>
        </>
    );
}

ProvideOpenAPI.defaultProps = {
    onValidate: () => { },
};
ProvideOpenAPI.INPUT_TYPES = {
    URL: 'url',
    FILE: 'file',
};
ProvideOpenAPI.propTypes = {
    apiInputs: PropTypes.shape({
        type: PropTypes.string,
        inputType: PropTypes.string,
        inputValue: PropTypes.string,
    }).isRequired,
    inputsDispatcher: PropTypes.func.isRequired,
    onValidate: PropTypes.func,
};
