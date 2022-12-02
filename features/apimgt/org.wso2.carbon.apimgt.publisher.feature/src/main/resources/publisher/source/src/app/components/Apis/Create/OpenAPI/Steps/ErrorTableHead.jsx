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
import TableCell from '@material-ui/core/TableCell';
import TableHead from '@material-ui/core/TableHead';
import TableRow from '@material-ui/core/TableRow';
import { FormattedMessage } from 'react-intl';
import PropTypes from 'prop-types';
import { makeStyles } from '@material-ui/core/styles';

const useStyles = makeStyles((theme) => ({
    errorTableHeader: {
        textAlign: 'left',
        padding: '0 0 0 2px',
        color: theme.palette.error.contrastText,
    },
    warningTableHeader: {
        textAlign: 'left',
        padding: '0 0 0 2px',
        color: theme.palette.warning.contrastText,
    },
}));

/**
 * @inheritdoc
 * @class ErrorTableHead
 * @extends {Component}
 */
const errorTableHead = (props) => {
    const classes = useStyles();
    const {
        errorTableHeader,
    } = props;
    const columnData = [
        {
            id: 'message',
            numeric: false,
            disablePadding: true,
            label: (<FormattedMessage
                id='API.Error.ErrorTableHead.message'
                defaultMessage='Message'
            />),
        },
        {
            id: 'description',
            numeric: false,
            disablePadding: true,
            label: (<FormattedMessage
                id='API.Error.ErrorTableHead.description'
                defaultMessage='Description'
            />),
        },
    ];
    return (
        <TableHead>
            <TableRow>
                {columnData.map((column) => {
                    return (
                        <TableCell
                            key={column.id}
                            align='left'
                            className={errorTableHeader ? classes.errorTableHeader : classes.warningTableHeader}
                        >
                            {column.label}
                        </TableCell>
                    );
                })}
            </TableRow>
        </TableHead>
    );
};

errorTableHead.defaultProps = {
    errorTableHeader: true,
};
errorTableHead.propTypes = {
    errorTableHeader: PropTypes.bool,
};
export default errorTableHead;
