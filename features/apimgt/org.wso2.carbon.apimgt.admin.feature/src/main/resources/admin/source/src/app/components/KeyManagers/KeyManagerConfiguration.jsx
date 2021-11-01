import React from 'react';
import TextField from '@material-ui/core/TextField';
import Checkbox from '@material-ui/core/Checkbox';
import FormControlLabel from '@material-ui/core/FormControlLabel';
import Box from '@material-ui/core/Box';
import FormLabel from '@material-ui/core/FormLabel';
import FormControl from '@material-ui/core/FormControl';
import FormGroup from '@material-ui/core/FormGroup';
import Radio from '@material-ui/core/Radio';
import RadioGroup from '@material-ui/core/RadioGroup';
import { FormattedMessage } from 'react-intl';
import InputLabel from '@material-ui/core/InputLabel';
import FormHelperText from '@material-ui/core/FormHelperText';
import { makeStyles } from '@material-ui/core/styles';
import CustomInputField from 'AppComponents/KeyManagers/CustomInputField';

const useStyles = makeStyles((theme) => ({
    inputLabel: {
        transform: 'translate(14px, 11px) scale(1)',
    },
    error: {
        color: theme.palette.error.dark,
    },
}));

/**
 * @export
 * @param {*} props sksk
 * @returns {React.Component}
 */
/**
 * Keymanager Connector configuration
 * @param {JSON} props props passed from parents.
 * @returns {JSX} key manager connector form.
 */
export default function KeyManagerConfiguration(props) {
    const {
        keymanagerConnectorConfigurations, additionalProperties,
        setAdditionalProperties, hasErrors, validating,
    } = props;
    const classes = useStyles();
    const onChange = (e) => {
        let finalValue;
        const { name, value, type } = e.target;
        if (type === 'checkbox') {
            if (additionalProperties[name]) {
                finalValue = additionalProperties[name];
            } else {
                finalValue = [];
            }
            if (e.target.checked) {
                finalValue.push(value);
            } else {
                const newValue = finalValue.filter((v) => v !== e.target.value);
                finalValue = newValue;
            }
        } else {
            finalValue = value;
        }
        setAdditionalProperties(name, finalValue);
    };
    const getComponent = (keymanagerConnectorConfiguration) => {
        let value = '';
        if (additionalProperties[keymanagerConnectorConfiguration.name]) {
            value = additionalProperties[keymanagerConnectorConfiguration.name];
        } else if (additionalProperties[keymanagerConnectorConfiguration.name] === undefined) {
            let defaultValue;
            value = keymanagerConnectorConfiguration.default;
            if (keymanagerConnectorConfiguration.type === 'select') {
                defaultValue = value.split(',');
            } else {
                defaultValue = value;
            }
            setAdditionalProperties(keymanagerConnectorConfiguration.name, defaultValue);
        }
        if (keymanagerConnectorConfiguration.type === 'input') {
            if (keymanagerConnectorConfiguration.mask) {
                return (
                    <FormControl variant='outlined' fullWidth>
                        <InputLabel className={classes.inputLabel}>
                            {keymanagerConnectorConfiguration.label}
                            {keymanagerConnectorConfiguration.required && (<span className={classes.error}>*</span>)}
                        </InputLabel>
                        <CustomInputField
                            value={value}
                            onChange={onChange}
                            name={keymanagerConnectorConfiguration.name}
                            required={keymanagerConnectorConfiguration.required}
                            hasErrors={hasErrors}
                            validating={validating}
                        />
                        <FormHelperText>
                            {hasErrors('keyconfig', value, true) || keymanagerConnectorConfiguration.tooltip}
                        </FormHelperText>
                    </FormControl>
                );
            }
            return (
                <TextField
                    margin='dense'
                    name={keymanagerConnectorConfiguration.name}
                    label={(
                        <span>
                            {keymanagerConnectorConfiguration.label}
                            {keymanagerConnectorConfiguration.required && (<span className={classes.error}>*</span>)}
                        </span>
                    )}
                    fullWidth
                    error={keymanagerConnectorConfiguration.required && hasErrors('keyconfig', value, true)}
                    helperText={hasErrors('keyconfig', value, true) || keymanagerConnectorConfiguration.tooltip}
                    variant='outlined'
                    value={value}
                    defaultValue={keymanagerConnectorConfiguration.default}
                    onChange={onChange}
                />
            );
        } else if (keymanagerConnectorConfiguration.type === 'select') {
            return (
                <FormControl
                    component='fieldset'
                    error={
                        keymanagerConnectorConfiguration.required
                        && (additionalProperties[keymanagerConnectorConfiguration.name] !== undefined)
                        && (additionalProperties[keymanagerConnectorConfiguration.name].length === 0)
                    }
                >
                    <FormLabel component='legend'>
                        <span>
                            {keymanagerConnectorConfiguration.label}
                            {keymanagerConnectorConfiguration.required && (<span className={classes.error}>*</span>)}
                        </span>
                    </FormLabel>
                    <FormGroup>
                        {keymanagerConnectorConfiguration.values.map((selection) => (
                            <FormControlLabel
                                control={(
                                    <Checkbox
                                        checked={value.includes(selection)}
                                        onChange={onChange}
                                        value={selection}
                                        color='primary'
                                        name={keymanagerConnectorConfiguration.name}
                                    />
                                )}
                                label={selection}
                            />
                        ))}
                    </FormGroup>
                    <FormHelperText>
                        {hasErrors('keyconfig', value, true) || keymanagerConnectorConfiguration.tooltip}
                    </FormHelperText>
                </FormControl>
            );
        } else if (keymanagerConnectorConfiguration.type === 'options') {
            return (
                <FormControl
                    component='fieldset'
                    error={
                        keymanagerConnectorConfiguration.required
                        && (additionalProperties[keymanagerConnectorConfiguration.name] !== undefined)
                        && (additionalProperties[keymanagerConnectorConfiguration.name].length === 0)
                    }
                >
                    <FormLabel component='legend'>
                        <span>
                            {keymanagerConnectorConfiguration.label}
                            {keymanagerConnectorConfiguration.required && (<span className={classes.error}>*</span>)}
                        </span>
                    </FormLabel>
                    <RadioGroup
                        aria-label={keymanagerConnectorConfiguration.label}
                        name={keymanagerConnectorConfiguration.name}
                        value={value}
                        onChange={onChange}
                    >
                        {keymanagerConnectorConfiguration.values.map((selection) => (
                            <FormControlLabel value={selection} control={<Radio />} label={selection} />
                        ))}
                    </RadioGroup>
                    <FormHelperText>
                        {hasErrors('keyconfig', value, true) || keymanagerConnectorConfiguration.tooltip}
                    </FormHelperText>
                </FormControl>
            );
        } else {
            return (
                <TextField
                    margin='dense'
                    name={keymanagerConnectorConfiguration.name}
                    label={keymanagerConnectorConfiguration.label}
                    fullWidth
                    required
                    variant='outlined'
                    value={value}
                    defaultValue={keymanagerConnectorConfiguration.default}
                    onChange={onChange}
                />
            );
        }
    };
    return (
        keymanagerConnectorConfigurations.map((keymanagerConnectorConfiguration) => (
            <Box mb={3}>
                {getComponent(keymanagerConnectorConfiguration, hasErrors, validating)}
            </Box>
        )));
}
KeyManagerConfiguration.defaultProps = {
    keymanagerConnectorConfigurations: [],
    required: false,
    helperText: <FormattedMessage
        id='KeyManager.Connector.Configuration.Helper.text'
        defaultMessage='Add Key Manager Connector Configurations'
    />,
    hasErrors: () => {},
    validating: false,
};
