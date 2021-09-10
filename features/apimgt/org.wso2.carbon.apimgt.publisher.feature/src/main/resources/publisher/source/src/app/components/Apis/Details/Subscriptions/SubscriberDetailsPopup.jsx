/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.com). All Rights Reserved.
 *
 * This software is the property of WSO2 Inc. and its suppliers, if any.
 * Dissemination of any information or reproduction of any material contained
 * herein is strictly forbidden, unless permitted by WSO2 in accordance with
 * the WSO2 Commercial License available at http://wso2.com/licenses.
 * For specific language governing the permissions and limitations under
 * this license, please see the license as well as any agreement you’ve
 * entered into with WSO2 governing the purchase of this software and any
 * associated services.
 */
import React from 'react';
import { useIntl, FormattedMessage } from 'react-intl';
import Popover from '@material-ui/core/Popover';
import Typography from '@material-ui/core/Typography';
import Table from '@material-ui/core/Table';
import TableBody from '@material-ui/core/TableBody';
import TableCell from '@material-ui/core/TableCell';
import TableRow from '@material-ui/core/TableRow';
import Box from '@material-ui/core/Box';
import UserIcon from '@material-ui/icons/Person';
import Alert from 'AppComponents/Shared/Alert';
import API from 'AppData/api';
import CircularProgress from '@material-ui/core/CircularProgress';
import InfoIcon from '@material-ui/icons/Info';


const PopoverContent = ({ subscriptionId }) => {
    const intl = useIntl();
    const [subscriberClaims, setSubscriberClaims] = React.useState(null);
    const api = new API();

    React.useEffect(() => {
        const promisedInfo = api.getSubscriberInfo(subscriptionId);
        promisedInfo
            .then((resp) => {
                if (resp.body) {
                    setSubscriberClaims(resp.body);
                } else {
                    setSubscriberClaims('no-data');
                }
            })
            .catch((errorMessage) => {
                console.error(errorMessage);
                Alert.error(intl.formatMessage({
                    id: 'Apis.Details.Subscriptions.SubscriberDetailsPopup.subscriber.info.error',
                    defaultMessage: 'Error while retrieving the subscriber information',
                }));
            });
    }, [subscriptionId]);
    if (!subscriberClaims) {
        return (<CircularProgress />);
    }
    if (subscriberClaims === 'no-data') {
        return (
            <div>
                <Typography>
                    <FormattedMessage
                        id='Apis.Details.Subscriptions.SubscriberDetailsPopup.no.claims'
                        defaultMessage='No subscriber claims data available'
                    />
                </Typography>
            </div>
        );
    }
    return (
        <Box p={2} alignItems='center' display='flex'>
            <Box p={2} display='flex' alignContent='center' justifyContent='center' flexDirection='column'>
                <UserIcon color='primary' />
                {subscriberClaims.name}
            </Box>
            {subscriberClaims.claims && (
                <Table>
                    <TableBody>
                        {subscriberClaims.claims.map((claim) => (
                            <TableRow hover>
                                <TableCell>{claim.name}</TableCell>
                                {claim.value ? (
                                    <TableCell>{claim.value}</TableCell>
                                ) : (
                                    <TableCell>
                                        <FormattedMessage
                                            id='Apis.Details.Subscriptions.SubscriberDetailsPopup.not.available'
                                            defaultMessage='Not Available'
                                        />
                                    </TableCell>
                                )}
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            )}
        </Box>
    );
};

/**
 * Adds two numbers together.
 * @param {JSON} props props to render the popup.
 * @returns {int} The sum of the two numbers.
 */
export default function SubscriberDetailsPopup(props) {
    const { subscriptionId, subscriber } = props;
    const [anchorEl, setAnchorEl] = React.useState(null);

    const handleClick = (event) => {
        setAnchorEl(event.currentTarget);
    };

    const handleClose = () => {
        setAnchorEl(null);
    };

    /**
     * Fetches subscription data
     * @param {string} subscriptionId subscriber of the application
     * @memberof SubscriptionsTable
     */


    const open = Boolean(anchorEl);
    const id = open ? 'subscriber-details-popper' : undefined;

    return (
        <Box p={2} alignItems='center' display='flex'>
            <Box alignItems='center' justifyContent='center' pr={1}>{subscriber}</Box>
            <InfoIcon aria-describedby={id} color='action' onClick={handleClick} style={{ cursor: 'pointer' }} />
            <Popover
                id={id}
                open={open}
                anchorEl={anchorEl}
                onClose={handleClose}
                anchorOrigin={{
                    vertical: 'bottom',
                    horizontal: 'center',
                }}
                transformOrigin={{
                    vertical: 'top',
                    horizontal: 'center',
                }}
            >
                <PopoverContent subscriptionId={subscriptionId} />
            </Popover>
        </Box>
    );
}
