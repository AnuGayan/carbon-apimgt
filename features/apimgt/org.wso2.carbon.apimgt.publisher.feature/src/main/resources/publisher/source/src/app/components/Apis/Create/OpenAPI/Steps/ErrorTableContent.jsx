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

import React, { Component } from 'react';
import TableBody from '@material-ui/core/TableBody';
import TableCell from '@material-ui/core/TableCell';
import TableRow from '@material-ui/core/TableRow';

import ResourceNotFound from 'AppComponents/Base/Errors/ResourceNotFound';
import PropTypes from 'prop-types';
import { withStyles } from '@material-ui/core/styles';

/**
 * @inheritdoc
 * @param {*} theme theme object
 */
const styles = (theme) => ({
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
});
const StyledTableCell = withStyles((theme) => ({
    head: {
        backgroundColor: theme.palette.common.black,
        color: theme.palette.common.white,
    },
    body: {
        fontSize: 11.5,
    },
    root: {
        padding: `0 0 0  ${theme.spacing(2)}px`,
    },
}))(TableCell);

const StyledTableRow = withStyles((theme) => ({
    root: {
        '&:nth-of-type(even)': {
            backgroundColor: theme.palette.background.default,
        },
        '& td': {
            borderRadius: '10px'
        },
    },
}))(TableRow);

/**
 *
 *
 * @class ErrorTableContent
 * @extends {Component}
 */
class ErrorTableContent extends Component {
    /**
     * @inheritdoc
     */
    constructor(props) {
        super(props);
        this.state = {
            notFound: false,
        };
    }

    /**
     * @inheritdoc
     * @memberof AppsTableContent
     */
    render() {
        const {
            errors, classes,
        } = this.props;
        const { notFound } = this.state;

        if (notFound) {
            return <ResourceNotFound />;
        }
        return (
            <TableBody>
                {errors.errors.map((error) => {
                    return (
                        <StyledTableRow className={classes.tableRow}>
                            <StyledTableCell align='left'>{
                                error.description.charAt(0).toUpperCase() + error.description.slice(1)
                            }</StyledTableCell>
                        </StyledTableRow>
                    );
                })}
            </TableBody>
        );
    }
}

ErrorTableContent.propTypes = {
    errors: PropTypes.instanceOf(Map).isRequired,
};
export default withStyles(styles)(ErrorTableContent);
