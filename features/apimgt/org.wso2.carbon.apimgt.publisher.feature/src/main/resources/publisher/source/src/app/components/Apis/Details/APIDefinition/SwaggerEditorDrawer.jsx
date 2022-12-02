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
import React, { lazy } from 'react';
import Grid from '@material-ui/core/Grid';
import PropTypes from 'prop-types';
import withStyles from '@material-ui/core/styles/withStyles';
import ErrorAccordion from 'AppComponents/Apis/Create/OpenAPI/Steps/ErrorAccordion';
import SwaggerUI from './swaggerUI/SwaggerUI';

const styles = () => ({
    editorPane: {
        width: '50%',
        height: '100%',
        overflow: 'scroll',
    },
    editorRoot: {
        height: '100%',
    },
    errorPane: {
        width: 'auto',
        paddingTop: '10px',
        paddingRight: '10px',
    },
    swaggerPane: {
        '& .swagger-ui': {
            width: '100%',
        },
    },
});

const MonacoEditor = lazy(() => import('react-monaco-editor' /* webpackChunkName: "APIDefMonacoEditor" */));

/**
 * This component hosts the Swagger Editor component.
 * Known Issue: The cursor jumps back to the start of the first line when updating the swagger-ui based on the
 * modification done via the editor.
 * https://github.com/wso2/product-apim/issues/5071
 * */
class SwaggerEditorDrawer extends React.Component {
    /**
     * @inheritDoc
     */
    constructor(props) {
        super(props);
        this.onContentChange = this.onContentChange.bind(this);
    }

    /**
     * Method to handle the change event of the editor.
     * @param {string} content : The edited content.
     * */
    onContentChange(content) {
        const { onEditContent } = this.props;
        onEditContent(content);
    }

    /**
     * @inheritDoc
     */
    render() {
        const {
            classes, language, swagger, errorDetails, noOfErrors, isValid,
        } = this.props;
        const swaggerUrl = 'data:text/' + language + ',' + encodeURIComponent(swagger);
        return (
            <>
                <Grid container spacing={2} className={classes.editorRoot}>
                    <Grid item className={classes.editorPane}>
                        <MonacoEditor
                            language={language}
                            width='100%'
                            height='calc(100vh - 51px)'
                            theme='vs-dark'
                            value={swagger}
                            onChange={this.onContentChange}
                            options={{ glyphMargin: true }}
                        />
                    </Grid>
                    <Grid item className={classes.editorPane}>
                        {isValid.file && (
                            <Grid item className={classes.errorPane}>
                                <ErrorAccordion
                                    errorDetails={errorDetails}
                                    noOfErrors={noOfErrors}
                                    isValid={isValid}
                                />
                            </Grid>
                        )}
                        <Grid item className={classes.swaggerPane}>
                            <SwaggerUI url={swaggerUrl} />
                        </Grid>
                    </Grid>
                </Grid>
            </>
        );
    }
}

SwaggerEditorDrawer.propTypes = {
    classes: PropTypes.shape({}).isRequired,
    language: PropTypes.string.isRequired,
    swagger: PropTypes.string.isRequired,
    onEditContent: PropTypes.func.isRequired,
    errorDetails: PropTypes.objectOf(PropTypes.object).isRequired,
    noOfErrors: PropTypes.number.isRequired,
    isValid: PropTypes.objectOf(PropTypes.object).isRequired,
};

export default withStyles(styles)(SwaggerEditorDrawer);
