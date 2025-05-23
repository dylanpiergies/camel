/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.catalog.impl;

import java.io.LineNumberReader;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.camel.catalog.ConfigurationPropertiesValidationResult;
import org.apache.camel.catalog.EndpointValidationResult;
import org.apache.camel.catalog.JSonSchemaResolver;
import org.apache.camel.catalog.LanguageValidationResult;
import org.apache.camel.catalog.SuggestionStrategy;
import org.apache.camel.tooling.model.ApiMethodModel;
import org.apache.camel.tooling.model.ApiModel;
import org.apache.camel.tooling.model.BaseModel;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.DevConsoleModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.tooling.model.MainModel;
import org.apache.camel.tooling.model.OtherModel;
import org.apache.camel.tooling.model.PojoBeanModel;
import org.apache.camel.tooling.model.TransformerModel;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ReflectionHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

import static org.apache.camel.util.StringHelper.isDashed;

/**
 * Base class for both the runtime RuntimeCamelCatalog from camel-core and the complete CamelCatalog from camel-catalog.
 */
public abstract class AbstractCamelCatalog {

    private static final Pattern SYNTAX_PATTERN = Pattern.compile("([\\w.]+)");
    private static final Pattern ENV_OR_SYS_PATTERN = Pattern.compile("\\{\\{(env|sys):\\w+\\}\\}");
    private static final Pattern SYNTAX_DASH_PATTERN = Pattern.compile("([\\w.-]+)");
    private static final Pattern COMPONENT_SYNTAX_PARSER = Pattern.compile("([^\\w-]*)([\\w-]+)");

    private SuggestionStrategy suggestionStrategy;
    private JSonSchemaResolver jsonSchemaResolver;

    public String componentJSonSchema(String name) {
        return jsonSchemaResolver.getComponentJSonSchema(name);
    }

    public String modelJSonSchema(String name) {
        return getJSonSchemaResolver().getModelJSonSchema(name);
    }

    public EipModel eipModel(String name) {
        String json = modelJSonSchema(name);
        return json != null ? JsonMapper.generateEipModel(json) : null;
    }

    public ComponentModel componentModel(String name) {
        String json = componentJSonSchema(name);
        return json != null ? JsonMapper.generateComponentModel(json) : null;
    }

    public String dataFormatJSonSchema(String name) {
        return getJSonSchemaResolver().getDataFormatJSonSchema(name);
    }

    public DataFormatModel dataFormatModel(String name) {
        String json = dataFormatJSonSchema(name);
        return json != null ? JsonMapper.generateDataFormatModel(json) : null;
    }

    public String languageJSonSchema(String name) {
        // if we try to look method then its in the bean.json file
        if ("method".equals(name)) {
            name = "bean";
        }
        return getJSonSchemaResolver().getLanguageJSonSchema(name);
    }

    public LanguageModel languageModel(String name) {
        String json = languageJSonSchema(name);
        return json != null ? JsonMapper.generateLanguageModel(json) : null;
    }

    public String transformerJSonSchema(String name) {
        return getJSonSchemaResolver().getTransformerJSonSchema(name);
    }

    public TransformerModel transformerModel(String name) {
        String json = transformerJSonSchema(name);
        return json != null ? JsonMapper.generateTransformerModel(json) : null;
    }

    public PojoBeanModel pojoBeanModel(String name) {
        String json = pojoBeanJSonSchema(name);
        return json != null ? JsonMapper.generatePojoBeanModel(json) : null;
    }

    public String pojoBeanJSonSchema(String name) {
        return getJSonSchemaResolver().getPojoBeanJSonSchema(name);
    }

    public String devConsoleJSonSchema(String name) {
        return getJSonSchemaResolver().getDevConsoleJSonSchema(name);
    }

    public DevConsoleModel devConsoleModel(String name) {
        String json = devConsoleJSonSchema(name);
        return json != null ? JsonMapper.generateDevConsoleModel(json) : null;
    }

    public String otherJSonSchema(String name) {
        return getJSonSchemaResolver().getOtherJSonSchema(name);
    }

    public OtherModel otherModel(String name) {
        String json = otherJSonSchema(name);
        return json != null ? JsonMapper.generateOtherModel(json) : null;
    }

    public String mainJSonSchema() {
        return getJSonSchemaResolver().getMainJsonSchema();
    }

    public MainModel mainModel() {
        String json = mainJSonSchema();
        return json != null ? JsonMapper.generateMainModel(json) : null;
    }

    public SuggestionStrategy getSuggestionStrategy() {
        return suggestionStrategy;
    }

    public void setSuggestionStrategy(SuggestionStrategy suggestionStrategy) {
        this.suggestionStrategy = suggestionStrategy;
    }

    public JSonSchemaResolver getJSonSchemaResolver() {
        return jsonSchemaResolver;
    }

    public void setJSonSchemaResolver(JSonSchemaResolver resolver) {
        this.jsonSchemaResolver = resolver;
    }

    public boolean validateTimePattern(String pattern) {
        return validateDuration(pattern);
    }

    public EndpointValidationResult validateEndpointProperties(String uri) {
        return validateEndpointProperties(uri, false, false, false);
    }

    public EndpointValidationResult validateEndpointProperties(String uri, boolean ignoreLenientProperties) {
        return validateEndpointProperties(uri, ignoreLenientProperties, false, false);
    }

    public EndpointValidationResult validateProperties(String scheme, Map<String, String> properties) {
        boolean lenient = Boolean.getBoolean(properties.getOrDefault("lenient", "false"));
        return validateProperties(scheme, properties, lenient, false, false);
    }

    private EndpointValidationResult validateProperties(
            String scheme, Map<String, String> properties,
            boolean lenient, boolean consumerOnly,
            boolean producerOnly) {
        EndpointValidationResult result = new EndpointValidationResult(scheme);

        ComponentModel model = componentModel(scheme);
        Map<String, BaseOptionModel> rows = new HashMap<>();
        model.getComponentOptions().forEach(o -> rows.put(o.getName(), o));
        // endpoint options have higher priority so overwrite component options
        model.getEndpointOptions().forEach(o -> rows.put(o.getName(), o));
        model.getEndpointPathOptions().forEach(o -> rows.put(o.getName(), o));

        if (model.isApi()) {
            String[] apiSyntax = StringHelper.splitWords(model.getApiSyntax());
            String key = properties.get(apiSyntax[0]);
            String key2 = apiSyntax.length > 1 ? properties.get(apiSyntax[1]) : null;
            Map<String, BaseOptionModel> apiProperties = extractApiProperties(model, key, key2);
            rows.putAll(apiProperties);
        }

        // the dataformat component refers to a data format so lets add the properties for the selected
        // data format to the list of rows
        if ("dataformat".equals(scheme)) {
            String dfName = properties.get("name");
            if (dfName != null) {
                DataFormatModel dfModel = dataFormatModel(dfName);
                if (dfModel != null) {
                    dfModel.getOptions().forEach(o -> rows.put(o.getName(), o));
                }
            }
        }

        for (Map.Entry<String, String> property : properties.entrySet()) {
            String value = property.getValue();
            String originalName = property.getKey();
            // the name may be using an optional prefix, so lets strip that because the options
            // in the schema are listed without the prefix
            String name = stripOptionalPrefixFromName(rows, originalName);
            // the name may be using a prefix, so lets see if we can find the real property name
            String propertyName = getPropertyNameFromNameWithPrefix(rows, name);
            if (propertyName != null) {
                name = propertyName;
            }
            BaseOptionModel row = rows.get(name);
            if (row == null) {
                // unknown option

                // only add as error if the component is not lenient properties, or not stub component
                // and the name is not a property placeholder for one or more values
                boolean namePlaceholder = name.startsWith("{{") && name.endsWith("}}");
                if (!namePlaceholder && !"stub".equals(scheme)) {
                    if (lenient) {
                        // as if we are lenient then the option is a dynamic extra option which we cannot validate
                        result.addLenient(name);
                    } else {
                        // its unknown
                        result.addUnknown(name);
                        if (suggestionStrategy != null) {
                            String[] suggestions = suggestionStrategy.suggestEndpointOptions(rows.keySet(), name);
                            if (suggestions != null) {
                                result.addUnknownSuggestions(name, suggestions);
                            }
                        }
                    }
                }
            } else {
                if ("parameter".equals(row.getKind())) {
                    // consumer only or producer only mode for parameters
                    String label = row.getLabel();
                    if (consumerOnly) {
                        if (label != null && label.contains("producer")) {
                            // the option is only for producer so you cannot use it in consumer mode
                            result.addNotConsumerOnly(name);
                        }
                    } else if (producerOnly) {
                        if (label != null && label.contains("consumer")) {
                            // the option is only for consumer so you cannot use it in producer mode
                            result.addNotProducerOnly(name);
                        }
                    }
                }

                String prefix = row.getPrefix();
                boolean valuePlaceholder = value.startsWith("{{") || value.startsWith("${") || value.startsWith("$simple{");
                boolean lookup = value.startsWith("#") && value.length() > 1;
                // we cannot evaluate multi values as strict as the others, as we don't know their expected types
                boolean multiValue = prefix != null && originalName.startsWith(prefix)
                        && row.isMultiValue();

                // default value
                Object defaultValue = row.getDefaultValue();
                if (defaultValue != null) {
                    result.addDefaultValue(name, defaultValue.toString());
                }

                // is required but the value is empty
                if (row.isRequired() && CatalogHelper.isEmpty(value)) {
                    result.addRequired(name);
                }

                // is the option deprecated
                boolean deprecated = row.isDeprecated();
                if (deprecated) {
                    result.addDeprecated(name);
                }

                // is enum but the value is not within the enum range
                // but we can only check if the value is not a placeholder
                List<String> enums = row.getEnums();
                if (!multiValue && !valuePlaceholder && !lookup && enums != null) {
                    boolean found = false;
                    for (String s : enums) {
                        String dashEC = StringHelper.camelCaseToDash(value);
                        String valueEC = StringHelper.asEnumConstantValue(value);
                        if (value.equalsIgnoreCase(s) || dashEC.equalsIgnoreCase(s) || valueEC.equalsIgnoreCase(s)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        handleNotFound(result, value, name, enums);
                    }
                }

                // is reference lookup of bean (not applicable for @UriPath, enums, or multi-valued)
                if (!multiValue && enums == null && !"path".equals(row.getKind()) && "object".equals(row.getType())) {
                    // must start with # and be at least 2 characters
                    if (!value.startsWith("#") || value.length() <= 1) {
                        result.addInvalidReference(name, value);
                    }
                }

                // is boolean
                if (!multiValue && !valuePlaceholder && !lookup && "boolean".equals(row.getType())) {
                    // value must be a boolean
                    boolean bool = ObjectHelper.isBoolean(value);
                    if (!bool) {
                        result.addInvalidBoolean(name, value);
                    }
                }

                // is duration
                if (!multiValue && !valuePlaceholder && !lookup && "duration".equals(row.getType())) {
                    // value must be convertable to a duration
                    boolean valid = validateDuration(value);
                    if (!valid) {
                        result.addInvalidDuration(name, value);
                    }
                }

                // is integer
                if (!multiValue && !valuePlaceholder && !lookup && "integer".equals(row.getType())) {
                    // value must be an integer
                    boolean valid = validateInteger(value);
                    if (!valid) {
                        result.addInvalidInteger(name, value);
                    }
                }

                // is number
                if (!multiValue && !valuePlaceholder && !lookup && "number".equals(row.getType())) {
                    // value must be an number
                    boolean valid = false;
                    try {
                        valid = !Double.valueOf(value).isNaN() || !Float.valueOf(value).isNaN();
                    } catch (Exception e) {
                        // ignore
                    }
                    if (!valid) {
                        result.addInvalidNumber(name, value);
                    }
                }
            }
        }

        // for api component then check that the apiName/methodName combo is valid
        if (model.isApi()) {
            String[] apiSyntax = StringHelper.splitWords(model.getApiSyntax());
            String key1 = properties.get(apiSyntax[0]);
            String key2 = apiSyntax.length > 1 ? properties.get(apiSyntax[1]) : null;

            if (key1 != null && key2 != null) {
                ApiModel api = model.getApiOptions().stream().filter(o -> o.getName().equalsIgnoreCase(key1)).findFirst()
                        .orElse(null);
                if (api == null) {
                    result.addInvalidEnum(apiSyntax[0], key1);
                    result.addInvalidEnumChoices(apiSyntax[0],
                            model.getApiOptions().stream().map(ApiModel::getName).toArray(String[]::new));
                } else {
                    // walk each method and match against its name/alias
                    boolean found = false;
                    for (ApiMethodModel m : api.getMethods()) {
                        String key3 = apiMethodAlias(api, m);
                        if (m.getName().equalsIgnoreCase(key2) || key2.equalsIgnoreCase(key3)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        result.addInvalidEnum(apiSyntax[1], key2);

                        result.addInvalidEnumChoices(apiSyntax[1], api.getMethods().stream()
                                .map(m -> {
                                    // favour using method alias in choices
                                    String answer = apiMethodAlias(api, m);
                                    if (answer == null) {
                                        answer = m.getName();
                                    }
                                    return answer;
                                }).toArray(String[]::new));
                    }
                }
            }
        }

        // now check if all required values are there, and that a default value does not exist
        for (BaseOptionModel row : rows.values()) {
            if (row.isRequired()) {
                String name = row.getName();
                Object value = properties.get(name);
                if (CatalogHelper.isEmpty(value)) {
                    value = row.getDefaultValue();
                }
                if (CatalogHelper.isEmpty(value)) {
                    result.addRequired(name);
                }
            }
        }

        return result;
    }

    private void handleNotFound(EndpointValidationResult result, String value, String name, List<String> enums) {
        result.addInvalidEnum(name, value);
        result.addInvalidEnumChoices(name, enums.toArray(new String[0]));
        if (suggestionStrategy != null) {
            Set<String> names = new LinkedHashSet<>(enums);
            String[] suggestions = suggestionStrategy.suggestEndpointOptions(names, value);
            if (suggestions != null) {
                result.addInvalidEnumSuggestions(name, suggestions);
            }
        }
    }

    private void handleNotFound(
            ConfigurationPropertiesValidationResult result, String value, String longKey, List<String> enums) {
        result.addInvalidEnum(longKey, value);
        result.addInvalidEnumChoices(longKey, enums.toArray(new String[0]));
        if (suggestionStrategy != null) {
            Set<String> names = new LinkedHashSet<>(enums);
            String[] suggestions = suggestionStrategy.suggestEndpointOptions(names, value);
            if (suggestions != null) {
                result.addInvalidEnumSuggestions(longKey, suggestions);
            }
        }
    }

    public EndpointValidationResult validateEndpointProperties(
            String uri, boolean ignoreLenientProperties, boolean consumerOnly, boolean producerOnly) {
        try {
            URI u = URISupport.normalizeUriAsURI(uri);
            String scheme = u.getScheme();
            ComponentModel model = scheme != null ? componentModel(scheme) : null;
            if (model == null) {
                EndpointValidationResult result = new EndpointValidationResult(uri);
                if (uri.startsWith("{{")) {
                    result.addIncapable(uri);
                } else if (scheme != null) {
                    result.addUnknownComponent(scheme);
                } else {
                    result.addUnknownComponent(uri);
                }
                return result;
            }
            Map<String, String> properties = endpointProperties(uri);
            boolean lenient;
            if (!model.isConsumerOnly() && !model.isProducerOnly() && consumerOnly) {
                // lenient properties is not support in consumer only mode if the component can do both of them
                lenient = false;
            } else {
                // only enable lenient properties if we should not ignore
                lenient = !ignoreLenientProperties && model.isLenientProperties();
            }
            return validateProperties(scheme, properties, lenient, consumerOnly, producerOnly);
        } catch (URISyntaxException e) {
            EndpointValidationResult result = new EndpointValidationResult(uri);
            result.addSyntaxError(e.getMessage());
            return result;
        }
    }

    public Map<String, String> endpointProperties(String uri) throws URISyntaxException {
        // need to normalize uri first
        URI u = URISupport.normalizeUriAsURI(uri);
        String scheme = u.getScheme();

        // grab the syntax
        ComponentModel model = componentModel(scheme);
        if (model == null) {
            throw new IllegalArgumentException("Cannot find endpoint with scheme " + scheme);
        }
        String syntax = model.getSyntax();
        String alternativeSyntax = model.getAlternativeSyntax();
        if (syntax == null) {
            throw new IllegalArgumentException("Endpoint with scheme " + scheme + " has no syntax defined in the json schema");
        }

        // only if we support alternative syntax, and the uri contains the username and password in the authority
        // part of the uri, then we would need some special logic to capture that information and strip those
        // details from the uri, so we can continue parsing the uri using the normal syntax
        Map<String, String> userInfoOptions = new LinkedHashMap<>();
        if (alternativeSyntax != null && alternativeSyntax.contains("@")) {
            // clip the scheme from the syntax
            alternativeSyntax = StringHelper.after(alternativeSyntax, ":");
            // trim so only userinfo
            int idx = alternativeSyntax.indexOf('@');
            String fields = alternativeSyntax.substring(0, idx);
            String[] names = fields.split(":");

            // grab authority part and grab username and/or password
            String authority = u.getAuthority();
            if (authority != null && authority.contains("@")) {
                String username;
                String password;

                // grab unserinfo part before @
                String userInfo = authority.substring(0, authority.indexOf('@'));
                String[] parts = userInfo.split(":");
                if (parts.length == 2) {
                    username = parts[0];
                    password = parts[1];
                } else {
                    // only username
                    username = userInfo;
                    password = null;
                }

                // remember the username and/or password which we add later to the options
                if (names.length == 2) {
                    userInfoOptions.put(names[0], username);
                    if (password != null) {
                        // password is optional
                        userInfoOptions.put(names[1], password);
                    }
                }
            }
        }

        // clip the scheme from the syntax
        syntax = StringHelper.after(syntax, ":");
        // clip the scheme from the uri
        uri = StringHelper.after(uri, ":");
        String uriPath = URISupport.stripQuery(uri);

        // the uri path may use {{env:xxx}} or {{sys:xxx}} placeholders so ignore those
        Matcher matcher = ENV_OR_SYS_PATTERN.matcher(uriPath);
        uriPath = matcher.replaceAll("");

        // strip user info from uri path
        if (!userInfoOptions.isEmpty()) {
            uriPath = StringHelper.after(uriPath, "@", uriPath);
        }

        // strip double slash in the start
        if (uriPath != null && uriPath.startsWith("//")) {
            uriPath = uriPath.substring(2);
        }

        // parse the syntax and find the names of each option
        matcher = SYNTAX_PATTERN.matcher(syntax);
        List<String> word = new ArrayList<>();
        while (matcher.find()) {
            String s = matcher.group(1);
            if (!scheme.equals(s)) {
                word.add(s);
            }
        }
        // parse the syntax and find each token between each option
        final List<String> word2 = findTokens(syntax, scheme, uriPath);

        boolean defaultValueAdded = false;

        // now parse the uri to know which part isw what
        Map<String, String> options = new LinkedHashMap<>();

        // include the username and password from the userinfo section
        if (!userInfoOptions.isEmpty()) {
            options.putAll(userInfoOptions);
        }

        Map<String, BaseOptionModel> rows = new HashMap<>();
        model.getComponentOptions().forEach(o -> rows.put(o.getName(), o));
        // endpoint options have higher priority so overwrite component options
        model.getEndpointOptions().forEach(o -> rows.put(o.getName(), o));
        model.getEndpointPathOptions().forEach(o -> rows.put(o.getName(), o));

        // is this an api component then there may be additional options
        if (model.isApi()) {
            String[] apiSyntax = StringHelper.splitWords(model.getSyntax());
            int pos = word.indexOf(apiSyntax[0]);
            if (pos != -1) {
                String key = word2.size() > pos ? word2.get(pos) : null;
                // key2 should be null as its fine to get all the options for api name
                Map<String, BaseOptionModel> apiProperties = extractApiProperties(model, key, null);
                rows.putAll(apiProperties);
            }
        }

        // word contains the syntax path elements
        Iterator<String> it = word2.iterator();
        for (int i = 0; i < word.size(); i++) {
            String key = word.get(i);
            BaseOptionModel option = rows.get(key);
            boolean allOptions = word.size() == word2.size();

            // we have all options so no problem
            if (allOptions) {
                String value = it.next();
                options.put(key, value);
            } else {
                // we have a little problem as we do not not have all options
                if (!option.isRequired()) {
                    Object value = null;

                    boolean last = i == word.size() - 1;
                    if (last) {
                        // if its the last value then use it instead of the default value
                        value = it.hasNext() ? it.next() : null;
                        if (value != null) {
                            options.put(key, value.toString());
                        } else {
                            value = option.getDefaultValue();
                        }
                    }
                    if (value != null) {
                        options.put(key, value.toString());
                        defaultValueAdded = true;
                    }
                } else {
                    String value = it.hasNext() ? it.next() : null;
                    if (value != null) {
                        options.put(key, value);
                    }
                }
            }
        }

        Map<String, String> answer = new LinkedHashMap<>();

        // remove all options which are using default values and are not required
        for (Map.Entry<String, String> entry : options.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            BaseOptionModel row = rows.get(key);
            if (defaultValueAdded) {
                boolean required = row.isRequired();
                Object defaultValue = row.getDefaultValue();

                if (!required && defaultValue != null) {
                    if (defaultValue.toString().equals(value)) {
                        continue;
                    }
                }
            }

            // we should keep this in the answer
            answer.put(key, value);
        }

        // now parse the uri parameters
        Map<String, Object> parameters = CatalogHelper.parseParameters(u);

        // and covert the values to String so its JMX friendly
        while (!parameters.isEmpty()) {
            Map.Entry<String, Object> entry = parameters.entrySet().iterator().next();
            String key = entry.getKey();
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            BaseOptionModel row = rows.get(key);
            if (row != null && row.isMultiValue()) {
                String prefix = row.getPrefix();
                if (prefix != null) {
                    // extra all the multi valued options
                    Map<String, Object> values = URISupport.extractProperties(parameters, prefix);
                    // build a string with the extra multi valued options with the prefix and & as separator
                    String csb = values.entrySet().stream()
                            .map(multi -> prefix + multi.getKey() + "="
                                          + (multi.getValue() != null ? multi.getValue().toString() : ""))
                            .collect(Collectors.joining("&"));
                    // append the extra multi-values to the existing (which contains the first multi value)
                    if (!csb.isEmpty()) {
                        value = value + "&" + csb;
                    }
                }
            }

            answer.put(key, value);
            // remove the parameter as we run in a while loop until no more parameters
            parameters.remove(key);
        }

        return answer;
    }

    private static List<String> findTokens(String syntax, String scheme, String uriPath) {
        String[] tokens = SYNTAX_PATTERN.split(syntax);

        // find the position where each option start/end
        List<String> word2 = new ArrayList<>();
        int prev = 0;
        int prevPath = 0;

        // special for activemq/jms where the enum for destinationType causes a token issue as it includes a colon
        // for 'temp:queue' and 'temp:topic' values
        if ("activemq".equals(scheme) || "jms".equals(scheme)) {
            if (uriPath.startsWith("temp:")) {
                prevPath = 5;
            }
        }

        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }

            // special for some tokens where :// can be used also, eg http://foo
            int idx = -1;
            int len = 0;
            if (":".equals(token)) {
                idx = uriPath.indexOf("://", prevPath);
                len = 3;
            }
            if (idx == -1) {
                idx = uriPath.indexOf(token, prevPath);
                len = token.length();
            }

            if (idx > 0) {
                String option = uriPath.substring(prev, idx);
                word2.add(option);
                prev = idx + len;
                prevPath = prev;
            }
        }
        // special for last or if we did not add anyone
        if (prev > 0 || word2.isEmpty()) {
            String option = uriPath.substring(prev);
            word2.add(option);
        }
        return word2;
    }

    private Map<String, BaseOptionModel> extractApiProperties(ComponentModel model, String key, String key2) {
        Map<String, BaseOptionModel> answer = new LinkedHashMap<>();
        if (key != null) {
            String matchKey = null;
            String dashKey = StringHelper.camelCaseToDash(key);
            String ecKey = StringHelper.asEnumConstantValue(key);
            String dashKey2 = StringHelper.camelCaseToDash(key2);
            String ecKey2 = StringHelper.asEnumConstantValue(key2);
            for (ApiModel am : model.getApiOptions()) {
                String aKey = am.getName();
                if (aKey.equalsIgnoreCase("DEFAULT") || aKey.equalsIgnoreCase(key) || aKey.equalsIgnoreCase(ecKey)
                        || aKey.equalsIgnoreCase(dashKey)) {
                    am.getMethods().stream()
                            .filter(m -> {
                                if (key2 == null) {
                                    // no api method so match all
                                    return true;
                                }
                                String name = m.getName();
                                if (name.equalsIgnoreCase(key2) || name.equalsIgnoreCase(ecKey2)
                                        || name.equalsIgnoreCase(dashKey2)) {
                                    return true;
                                }
                                // is there an alias then we need to compute the alias key and compare against the key2
                                String key3 = apiMethodAlias(am, m);
                                if (key3 != null) {
                                    String dashKey3 = StringHelper.camelCaseToDash(key3);
                                    String ecKey3 = StringHelper.asEnumConstantValue(key3);
                                    if (key2.equalsIgnoreCase(key3) || ecKey2.equalsIgnoreCase(ecKey3)
                                            || dashKey2.equalsIgnoreCase(dashKey3)) {
                                        return true;
                                    }
                                }
                                return false;
                            })
                            .forEach(m -> m.getOptions()
                                    .forEach(o -> answer.put(o.getName(), o)));
                }
            }
        }
        return answer;
    }

    private static String apiMethodAlias(ApiModel api, ApiMethodModel method) {
        String name = method.getName();
        for (String alias : api.getAliases()) {
            int pos = alias.indexOf('=');
            String pattern = alias.substring(0, pos);
            String aliasMethod = alias.substring(pos + 1);
            // match ignore case
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(name).matches()) {
                return aliasMethod;
            }
        }
        return null;
    }

    public Map<String, String> endpointLenientProperties(String uri) throws URISyntaxException {
        // need to normalize uri first

        // parse the uri
        URI u = URISupport.normalizeUriAsURI(uri);
        String scheme = u.getScheme();

        ComponentModel model = componentModel(scheme);
        if (model == null) {
            throw new IllegalArgumentException("Cannot find endpoint with scheme " + scheme);
        }
        Map<String, BaseOptionModel> rows = new HashMap<>();
        model.getComponentOptions().forEach(o -> rows.put(o.getName(), o));
        // endpoint options have higher priority so overwrite component options
        model.getEndpointOptions().forEach(o -> rows.put(o.getName(), o));
        model.getEndpointPathOptions().forEach(o -> rows.put(o.getName(), o));

        // now parse the uri parameters
        Map<String, Object> parameters = URISupport.parseParameters(u);

        // all the known options
        Set<String> names = rows.keySet();

        Map<String, String> answer = new LinkedHashMap<>();

        // and covert the values to String so its JMX friendly
        parameters.forEach((key, v) -> {
            String value = v != null ? v.toString() : "";

            // is the key a prefix property
            int dot = key.indexOf('.');
            if (dot != -1) {
                String prefix = key.substring(0, dot + 1); // include dot in prefix
                String option = getPropertyNameFromNameWithPrefix(rows, prefix);
                if (option == null || !rows.get(option).isMultiValue()) {
                    answer.put(key, value);
                }
            } else if (!names.contains(key)) {
                answer.put(key, value);
            }
        });

        return answer;
    }

    public String endpointComponentName(String uri) {
        if (uri != null) {
            return StringHelper.before(uri, ":");
        }
        return null;
    }

    public String asEndpointUri(String scheme, Map<String, String> properties, boolean encode) {
        return doAsEndpointUri(scheme, properties, "&", encode);
    }

    public String asEndpointUriXml(String scheme, Map<String, String> properties, boolean encode) {
        return doAsEndpointUri(scheme, properties, "&amp;", encode);
    }

    String doAsEndpointUri(String scheme, Map<String, String> properties, String ampersand, boolean encode) {
        // grab the syntax
        ComponentModel model = componentModel(scheme);
        if (model == null) {
            throw new IllegalArgumentException("Cannot find endpoint with scheme " + scheme);
        }
        String originalSyntax = model.getSyntax();
        if (originalSyntax == null) {
            throw new IllegalArgumentException("Endpoint with scheme " + scheme + " has no syntax defined in the json schema");
        }

        // do any properties filtering which can be needed for some special components
        properties = filterProperties(scheme, properties);

        Map<String, BaseOptionModel> rows = new HashMap<>();
        model.getComponentOptions().forEach(o -> rows.put(o.getName(), o));
        // endpoint options have higher priority so overwrite component options
        model.getEndpointOptions().forEach(o -> rows.put(o.getName(), o));
        model.getEndpointPathOptions().forEach(o -> rows.put(o.getName(), o));

        if (originalSyntax.contains(":")) {
            originalSyntax = StringHelper.after(originalSyntax, ":");
        }

        // build at first according to syntax (use a tree map as we want the uri options sorted)
        Map<String, String> copy = new TreeMap<>(properties);

        Matcher syntaxMatcher = COMPONENT_SYNTAX_PARSER.matcher(originalSyntax);
        StringBuilder sb = new StringBuilder();
        while (syntaxMatcher.find()) {
            sb.append(syntaxMatcher.group(1));
            String propertyName = syntaxMatcher.group(2);
            String propertyValue = copy.remove(propertyName);
            sb.append(propertyValue != null ? propertyValue : propertyName);
        }
        // clip the scheme from the syntax
        String syntax = sb.toString();

        // do we have all the options the original syntax needs (easy way)
        String[] keys = syntaxKeys(originalSyntax);
        boolean hasAllKeys = properties.keySet().containsAll(Arrays.asList(keys));

        // build endpoint uri
        sb = new StringBuilder();
        // add scheme later as we need to take care if there is any context-path or query parameters which
        // affect how the URI should be constructed

        if (hasAllKeys) {
            // we have all the keys for the syntax so we can build the uri the easy way
            sb.append(syntax);

            if (!copy.isEmpty()) {
                // wrap secret values with RAW to avoid breaking URI encoding in case of encoded values
                copy.replaceAll((key, val) -> wrapRAW(key, val, rows));

                boolean hasQuestionMark = sb.toString().contains("?");
                // the last option may already contain a ? char, if so we should use & instead of ?
                sb.append(hasQuestionMark ? ampersand : '?');
                String query = URISupport.createQueryString(copy, ampersand, encode);
                sb.append(query);
            }
        } else {
            // TODO: revisit this and see if we can do this in another way
            // oh darn some options is missing, so we need a complex way of building the uri

            // the tokens between the options in the path
            String[] tokens = SYNTAX_DASH_PATTERN.split(syntax);

            // parse the syntax into each options
            Matcher matcher = SYNTAX_PATTERN.matcher(originalSyntax);
            List<String> options = new ArrayList<>();
            while (matcher.find()) {
                String s = matcher.group(1);
                options.add(s);
            }

            // need to preserve {{ and }} from the syntax
            // (we need to use words only as its provisional placeholders)
            syntax = syntax.replace("{{", "BEGINCAMELPLACEHOLDER");
            syntax = syntax.replace("}}", "ENDCAMELPLACEHOLDER");

            // parse the syntax into each options
            Matcher matcher2 = SYNTAX_DASH_PATTERN.matcher(syntax);
            List<String> options2 = new ArrayList<>();
            while (matcher2.find()) {
                String s = matcher2.group(1);
                s = s.replace("BEGINCAMELPLACEHOLDER", "{{");
                s = s.replace("ENDCAMELPLACEHOLDER", "}}");
                options2.add(s);
            }

            // build the endpoint
            int range = 0;
            boolean first = true;
            boolean hasQuestionmark = false;
            for (int i = 0; i < options.size(); i++) {
                String key = options.get(i);
                String key2 = options2.get(i);
                String token = null;
                if (tokens.length > i) {
                    token = tokens[i];
                }

                boolean contains = properties.containsKey(key);
                if (!contains) {
                    // if the key are similar we have no explicit value and can try to find a default value if the option is required
                    BaseOptionModel row = rows.get(key);
                    if (row != null && row.isRequired()) {
                        Object value = row.getDefaultValue();
                        if (!CatalogHelper.isEmpty(value)) {
                            properties.put(key, key2 = value.toString());
                        }
                    }
                }

                // was the option provided?
                if (properties.containsKey(key)) {
                    if (!first && token != null) {
                        sb.append(token);
                    }
                    hasQuestionmark |= key.contains("?") || (token != null && token.contains("?"));
                    sb.append(key2);
                    first = false;
                }
                range++;
            }
            // append any extra options that was in surplus for the last
            while (range < options2.size()) {
                String token = null;
                if (tokens.length > range) {
                    token = tokens[range];
                }
                String key2 = options2.get(range);
                sb.append(token);
                sb.append(key2);
                hasQuestionmark |= key2.contains("?") || (token != null && token.contains("?"));
                range++;
            }

            if (!copy.isEmpty()) {
                // wrap secret values with RAW to avoid breaking URI encoding in case of encoded values
                copy.replaceAll((key, val) -> wrapRAW(key, val, rows));

                // the last option may already contain a ? char, if so we should use & instead of ?
                sb.append(hasQuestionmark ? ampersand : '?');
                String query = URISupport.createQueryString(copy, ampersand, encode);
                sb.append(query);
            }
        }

        String remainder = sb.toString();
        boolean queryOnly = remainder.startsWith("?");
        if (queryOnly) {
            // it has only query parameters
            return scheme + remainder;
        } else if (!remainder.isEmpty()) {
            // it has context path and possible query parameters
            return scheme + ":" + remainder;
        } else {
            // its empty without anything
            return scheme;
        }
    }

    private static String wrapRAW(String key, String val, Map<String, BaseOptionModel> rows) {
        if (val == null) {
            return val;
        }
        BaseOptionModel option = rows.get(key);
        if (option == null) {
            return val;
        }

        if (option.isSecret() && !val.startsWith("#") && !val.startsWith("RAW(")) {
            return "RAW(" + val + ")";
        }

        return val;
    }

    private static String[] syntaxKeys(String syntax) {
        // build tokens between the separators
        List<String> tokens = new ArrayList<>();

        if (syntax != null) {
            StringBuilder current = new StringBuilder();
            for (int i = 0; i < syntax.length(); i++) {
                char ch = syntax.charAt(i);
                if (Character.isLetterOrDigit(ch)) {
                    current.append(ch);
                } else {
                    // reset for new current tokens
                    if (current.length() > 0) {
                        tokens.add(current.toString());
                        current = new StringBuilder();
                    }
                }
            }
            // anything left over?
            if (!current.isEmpty()) {
                tokens.add(current.toString());
            }
        }

        return tokens.toArray(new String[0]);
    }

    public ConfigurationPropertiesValidationResult validateConfigurationProperty(String line) {
        String longKey = StringHelper.before(line, "=");
        String key = longKey;
        String value = StringHelper.after(line, "=");
        // trim values
        if (longKey != null) {
            longKey = longKey.trim();
        }
        if (key != null) {
            key = key.trim();
        }
        if (value != null) {
            value = value.trim();
        }

        ConfigurationPropertiesValidationResult result = new ConfigurationPropertiesValidationResult();
        boolean accept = acceptConfigurationPropertyKey(key);
        if (!accept) {
            result.setAccepted(false);
            return result;
        } else {
            result.setAccepted(true);
        }
        // skip camel.
        key = key.substring("camel.".length());

        Function<String, ? extends BaseModel<?>> loader = null;
        if (key.startsWith("component.")) {
            key = key.substring("component.".length());
            loader = this::componentModel;
        } else if (key.startsWith("dataformat.")) {
            key = key.substring("dataformat.".length());
            loader = this::dataFormatModel;
        } else if (key.startsWith("language.")) {
            key = key.substring("language.".length());
            loader = this::languageModel;
        }
        if (loader != null) {
            int idx = key.indexOf('.');
            String name = key.substring(0, idx);
            String option = key.substring(idx + 1);

            if (value != null) {
                BaseModel<?> model = loader.apply(name);
                if (model == null) {
                    result.addUnknownComponent(name);
                    return result;
                }
                Map<String, BaseOptionModel> rows = new HashMap<>();
                model.getOptions().forEach(o -> rows.put(o.getName(), o));

                // lower case option and remove dash
                String nOption = option.replace("-", "").toLowerCase(Locale.ENGLISH);
                String suffix = null;
                int posDot = nOption.indexOf('.');
                int posBracket = nOption.indexOf('[');
                validateConfigurationProperty(posDot, posBracket, suffix, nOption, result, rows, name, value, longKey);
            }
        } else if (key.startsWith("main.")
                || key.startsWith("resilience4j.")
                || key.startsWith("faulttolerance.")
                || key.startsWith("threadpool.")
                || key.startsWith("lra.")
                || key.startsWith("health.")
                || key.startsWith("rest.")) {
            String name = StringHelper.before(key, ".");
            if (value != null) {
                MainModel model = mainModel();
                if (model == null) {
                    result.addIncapable("camel-main not detected on classpath");
                    return result;
                }
                Map<String, BaseOptionModel> rows = new HashMap<>();
                model.getOptions().forEach(o -> rows.put(dashToCamelCase(o.getName()), o));

                // lower case option and remove dash
                String nOption = longKey.replace("-", "").toLowerCase(Locale.ENGLISH);

                // look for suffix or array index after 2nd dot
                int secondDot = nOption.indexOf('.', nOption.indexOf('.') + 1) + 1;

                String suffix = null;
                int posDot = nOption.indexOf('.', secondDot);
                int posBracket = nOption.indexOf('[', secondDot);
                validateConfigurationProperty(posDot, posBracket, suffix, nOption, result, rows, name, value, longKey);
            }
        }

        return result;
    }

    private void validateConfigurationProperty(
            int posDot, int posBracket, String suffix, String nOption, ConfigurationPropertiesValidationResult result,
            Map<String, BaseOptionModel> rows, String name, String value, String longKey) {
        if (posDot > 0 && posBracket > 0) {
            int first = Math.min(posDot, posBracket);
            suffix = nOption.substring(first);
            nOption = nOption.substring(0, first);
        } else if (posDot > 0) {
            suffix = nOption.substring(posDot);
            nOption = nOption.substring(0, posDot);
        } else if (posBracket > 0) {
            suffix = nOption.substring(posBracket);
            nOption = nOption.substring(0, posBracket);
        }

        doValidateConfigurationProperty(result, rows, name, value, longKey, nOption, suffix);
    }

    private void doValidateConfigurationProperty(
            ConfigurationPropertiesValidationResult result,
            Map<String, BaseOptionModel> rows,
            String name, String value, String longKey,
            String lookupKey, String suffix) {

        // find option
        String rowKey = rows.keySet().stream()
                .filter(n -> n.toLowerCase(Locale.ENGLISH).equals(lookupKey)).findFirst().orElse(null);
        if (rowKey == null) {
            // unknown option
            result.addUnknown(longKey);
            if (suggestionStrategy != null) {
                String[] suggestions = suggestionStrategy.suggestEndpointOptions(rows.keySet(), name);
                if (suggestions != null) {
                    result.addUnknownSuggestions(name, suggestions);
                }
            }
        } else {
            boolean optionPlaceholder = value.startsWith("{{") || value.startsWith("${") || value.startsWith("$simple{");
            boolean lookup = value.startsWith("#") && value.length() > 1;

            // deprecated
            BaseOptionModel row = rows.get(rowKey);
            if (!optionPlaceholder && !lookup && row.isDeprecated()) {
                result.addDeprecated(longKey);
            }

            // is boolean
            if (!optionPlaceholder && !lookup && "boolean".equals(row.getType())) {
                // value must be a boolean
                boolean bool = ObjectHelper.isBoolean(value);
                if (!bool) {
                    result.addInvalidBoolean(longKey, value);
                }
            }

            // is duration
            if (!optionPlaceholder && !lookup && "duration".equals(row.getType())) {
                // value must be convertable to a duration
                boolean valid = validateDuration(value);
                if (!valid) {
                    result.addInvalidDuration(longKey, value);
                }
            }

            // is integer
            if (!optionPlaceholder && !lookup && "integer".equals(row.getType())) {
                // value must be an integer
                boolean valid = validateInteger(value);
                if (!valid) {
                    result.addInvalidInteger(longKey, value);
                }
            }

            // is number
            if (!optionPlaceholder && !lookup && "number".equals(row.getType())) {
                // value must be an number
                boolean valid = false;
                try {
                    valid = !Double.valueOf(value).isNaN() || !Float.valueOf(value).isNaN();
                } catch (Exception e) {
                    // ignore
                }
                if (!valid) {
                    result.addInvalidNumber(longKey, value);
                }
            }

            // is enum
            List<String> enums = row.getEnums();
            if (!optionPlaceholder && !lookup && enums != null) {
                boolean found = false;
                String dashEC = StringHelper.camelCaseToDash(value);
                String valueEC = StringHelper.asEnumConstantValue(value);
                for (String s : enums) {
                    // equals as is or using the enum naming style
                    if (value.equalsIgnoreCase(s) || dashEC.equalsIgnoreCase(s) || valueEC.equalsIgnoreCase(s)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    handleNotFound(result, value, longKey, enums);
                }
            }

            String javaType = row.getJavaType();
            if (!optionPlaceholder && !lookup && javaType != null
                    && (javaType.startsWith("java.util.Map") || javaType.startsWith("java.util.Properties"))) {
                // there must be a valid suffix
                if (isValidSuffix(suffix)) {
                    result.addInvalidMap(longKey, value);
                } else if (suffix.startsWith("[") && !suffix.contains("]")) {
                    result.addInvalidMap(longKey, value);
                }
            }
            if (!optionPlaceholder && !lookup && javaType != null && "array".equals(row.getType())) {
                // there must be a suffix and it must be using [] style
                if (isValidSuffix(suffix)) {
                    result.addInvalidArray(longKey, value);
                } else if (!suffix.startsWith("[") && !suffix.contains("]")) {
                    result.addInvalidArray(longKey, value);
                } else {
                    String index = StringHelper.before(suffix.substring(1), "]");
                    // value must be an integer
                    boolean valid = validateInteger(index);
                    if (!valid) {
                        result.addInvalidInteger(longKey, index);
                    }
                }
            }
        }
    }

    private static boolean isValidSuffix(String suffix) {
        return suffix == null || suffix.isEmpty() || suffix.equals(".");
    }

    private static boolean acceptConfigurationPropertyKey(String key) {
        if (key == null) {
            return false;
        }
        return key.startsWith("camel.component.")
                || key.startsWith("camel.dataformat.")
                || key.startsWith("camel.language.")
                || key.startsWith("camel.main.")
                || key.startsWith("camel.resilience4j.")
                || key.startsWith("camel.faulttolerance.")
                || key.startsWith("camel.threadpool.")
                || key.startsWith("camel.health.")
                || key.startsWith("camel.lra.")
                || key.startsWith("camel.rest.");
    }

    private LanguageValidationResult doValidateSimple(ClassLoader classLoader, String simple, boolean predicate) {
        if (classLoader == null) {
            classLoader = getClass().getClassLoader();
        }

        // if there are {{ }}} property placeholders then we need to resolve them to something else
        // as the simple parse cannot resolve them before parsing as we dont run the actual Camel application
        // with property placeholders setup so we need to dummy this by replace the {{ }} to something else
        // therefore we use a more unlikely character: {{XXX}} to ~^XXX^~
        String resolved = simple.replaceAll("\\{\\{(.+)\\}\\}", "~^$1^~");

        LanguageValidationResult answer = new LanguageValidationResult(simple);

        Object context;
        Object instance = null;
        Class<?> clazz;

        try {
            // need a simple camel context for the simple language parser to be able to parse
            clazz = classLoader.loadClass("org.apache.camel.impl.engine.SimpleCamelContext");
            context = clazz.getDeclaredConstructor(boolean.class).newInstance(false);
            clazz = classLoader.loadClass("org.apache.camel.language.simple.SimpleLanguage");
            instance = clazz.getDeclaredConstructor().newInstance();
            clazz = classLoader.loadClass("org.apache.camel.CamelContext");
            instance.getClass().getMethod("setCamelContext", clazz).invoke(instance, context);
        } catch (Exception e) {
            clazz = null;
            answer.setError(e.getMessage());
        }

        if (clazz != null) {
            Throwable cause = null;
            try {
                if (predicate) {
                    instance.getClass().getMethod("createPredicate", String.class).invoke(instance, resolved);
                } else {
                    instance.getClass().getMethod("createExpression", String.class).invoke(instance, resolved);
                }
            } catch (InvocationTargetException e) {
                cause = e.getTargetException();
            } catch (Exception e) {
                cause = e;
            }

            if (cause != null) {

                // reverse ~^XXX^~ back to {{XXX}}
                String errMsg = cause.getMessage();
                errMsg = errMsg.replaceAll("\\~\\^(.+)\\^\\~", "{{$1}}");

                answer.setError(errMsg);

                // is it simple parser exception then we can grab the index where the problem is
                if (cause.getClass().getName().equals("org.apache.camel.language.simple.types.SimpleIllegalSyntaxException")
                        || cause.getClass().getName().equals("org.apache.camel.language.simple.types.SimpleParserException")) {
                    try {
                        // we need to grab the index field from those simple parser exceptions
                        Method method = cause.getClass().getMethod("getIndex");
                        Object result = method.invoke(cause);
                        if (result != null) {
                            int index = (int) result;
                            answer.setIndex(index);
                        }
                    } catch (Exception i) {
                        // ignore
                    }
                }

                // we need to grab the short message field from this simple syntax exception
                if (cause.getClass().getName().equals("org.apache.camel.language.simple.types.SimpleIllegalSyntaxException")) {
                    try {
                        Method method = cause.getClass().getMethod("getShortMessage");
                        Object result = method.invoke(cause);
                        if (result != null) {
                            String msg = (String) result;
                            answer.setShortError(msg);
                        }
                    } catch (Exception i) {
                        // ignore
                    }

                    if (answer.getShortError() == null) {
                        // fallback and try to make existing message short instead
                        String msg = answer.getError();
                        // grab everything before " at location " which would be regarded as the short message
                        int idx = msg.indexOf(" at location ");
                        if (idx > 0) {
                            msg = msg.substring(0, idx);
                            answer.setShortError(msg);
                        }
                    }
                }
            }
        }

        return answer;
    }

    private LanguageValidationResult doValidateGroovy(ClassLoader classLoader, String groovy, boolean predicate) {
        if (classLoader == null) {
            classLoader = getClass().getClassLoader();
        }

        // if there are {{ }}} property placeholders then we need to resolve them to something else
        // as the simple parse cannot resolve them before parsing as we dont run the actual Camel application
        // with property placeholders setup so we need to dummy this by replace the {{ }} to something else
        // therefore we use a more unlikely character: {{XXX}} to ~^XXX^~
        String resolved = groovy.replaceAll("\\{\\{(.+)\\}\\}", "~^$1^~");

        LanguageValidationResult answer = new LanguageValidationResult(groovy);

        Object context;
        Object instance = null;
        Class<?> clazz;

        try {
            // need a simple camel context for the groovy language parser to be able to parse
            clazz = classLoader.loadClass("org.apache.camel.impl.engine.SimpleCamelContext");
            context = clazz.getDeclaredConstructor(boolean.class).newInstance(false);
            clazz = classLoader.loadClass("org.apache.camel.language.groovy.GroovyLanguage");
            instance = clazz.getDeclaredConstructor().newInstance();
            clazz = classLoader.loadClass("org.apache.camel.CamelContext");
            instance.getClass().getMethod("setCamelContext", clazz).invoke(instance, context);
        } catch (Exception e) {
            clazz = null;
            answer.setError(e.getMessage());
        }

        if (clazz != null) {
            Throwable cause = null;
            try {
                if (predicate) {
                    instance.getClass().getMethod("validatePredicate", String.class).invoke(instance, resolved);
                } else {
                    instance.getClass().getMethod("validateExpression", String.class).invoke(instance, resolved);
                }
            } catch (InvocationTargetException e) {
                cause = e.getTargetException();
            } catch (Exception e) {
                cause = e;
            }

            if (cause != null) {

                // reverse ~^XXX^~ back to {{XXX}}
                String errMsg = cause.getMessage();
                errMsg = errMsg.replaceAll("\\~\\^(.+)\\^\\~", "{{$1}}");

                answer.setError(errMsg);

                // is it simple parser exception then we can grab the index where the problem is
                if (cause.getClass().getName().equals("org.apache.camel.language.groovy.GroovyValidationException")) {
                    try {
                        // we need to grab the index field from those simple parser exceptions
                        Method method = cause.getClass().getMethod("getIndex");
                        Object result = method.invoke(cause);
                        if (result != null) {
                            int index = (int) result;
                            answer.setIndex(index);
                        }
                    } catch (Exception i) {
                        // ignore
                    }
                }

                // we need to grab the short message field from this simple syntax exception
                if (answer.getShortError() == null) {
                    // fallback and try to make existing message short instead
                    String msg = answer.getError();
                    // grab everything before " @ " which would be regarded as the short message
                    LineNumberReader lnr = new LineNumberReader(new StringReader(msg));
                    try {
                        String line = lnr.readLine();
                        do {
                            if (line.contains(" @ ")) {
                                // skip leading Scrip_xxxx.groovy: N:
                                if (line.startsWith("Script_") && StringHelper.countChar(line, ':') > 2) {
                                    line = StringHelper.after(line, ":", line);
                                    line = StringHelper.after(line, ":", line);
                                    line = line.trim();
                                }
                                answer.setShortError(line);
                                break;
                            }
                            line = lnr.readLine();
                        } while (line != null);
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
        }

        return answer;
    }

    public LanguageValidationResult validateLanguagePredicate(ClassLoader classLoader, String language, String text) {
        if ("simple".equals(language)) {
            return doValidateSimple(classLoader, text, true);
        } else if ("groovy".equals(language)) {
            return doValidateGroovy(classLoader, text, true);
        } else {
            return doValidateLanguage(classLoader, language, text, true);
        }
    }

    public LanguageValidationResult validateLanguageExpression(ClassLoader classLoader, String language, String text) {
        if ("simple".equals(language)) {
            return doValidateSimple(classLoader, text, false);
        } else if ("groovy".equals(language)) {
            return doValidateGroovy(classLoader, text, false);
        } else {
            return doValidateLanguage(classLoader, language, text, false);
        }
    }

    private LanguageValidationResult doValidateLanguage(
            ClassLoader classLoader, String language, String text, boolean predicate) {
        if (classLoader == null) {
            classLoader = getClass().getClassLoader();
        }

        LanguageValidationResult answer = new LanguageValidationResult(text);

        Map<String, Object> options = null;
        if (language.contains("?")) {
            String query = URISupport.extractQuery(language);
            language = StringHelper.before(language, "?");
            try {
                options = URISupport.parseQuery(query);
            } catch (Exception e) {
                answer.setError("Cannot parse language options: " + query);
                return answer;
            }
        }

        LanguageModel model = languageModel(language);
        if (model == null) {
            answer.setError("Unknown language " + language);
            return answer;
        }
        String className = model.getJavaType();
        if (className == null) {
            answer.setError("Cannot find javaType for language " + language);
            return answer;
        }

        Object instance = null;
        Class<?> clazz = null;
        try {
            clazz = classLoader.loadClass(className);
            instance = clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            // ignore
        }
        // set options on the language
        if (options != null) {
            final Map<String, Object> fOptions = options;
            final Object fInstance = instance;
            ReflectionHelper.doWithFields(clazz, field -> {
                Object value = fOptions.get(field.getName());
                if (value != null) {
                    ReflectionHelper.setField(field, fInstance, value);
                }
            });
        }

        if (clazz != null && instance != null) {
            Throwable cause = null;
            Object obj;
            try {
                try {
                    // favour using the validate method if present as this is for tooling usage
                    if (predicate) {
                        instance.getClass().getMethod("validatePredicate", String.class).invoke(instance, text);
                    } else {
                        instance.getClass().getMethod("validateExpression", String.class).invoke(instance, text);
                    }
                    return answer;
                } catch (NoSuchMethodException e) {
                    // ignore
                }
                if (predicate) {
                    instance.getClass().getMethod("createPredicate", String.class).invoke(instance, text);
                } else {
                    instance.getClass().getMethod("createExpression", String.class).invoke(instance, text);
                }
            } catch (InvocationTargetException e) {
                cause = e.getTargetException();
            } catch (Exception e) {
                cause = e;
            }

            if (cause != null) {
                answer.setError(cause.getMessage());
            }
        }

        return answer;
    }

    /**
     * Special logic for log endpoints to deal when showAll=true
     */
    private Map<String, String> filterProperties(String scheme, Map<String, String> options) {
        if ("log".equals(scheme)) {
            String showAll = options.get("showAll");
            if ("true".equals(showAll)) {
                Map<String, String> filtered = new LinkedHashMap<>();
                // remove all the other showXXX options when showAll=true
                for (Map.Entry<String, String> entry : options.entrySet()) {
                    String key = entry.getKey();
                    boolean skip = key.startsWith("show") && !key.equals("showAll");
                    if (!skip) {
                        filtered.put(key, entry.getValue());
                    }
                }
                return filtered;
            }
        }
        // use as-is
        return options;
    }

    private static boolean validateInteger(String value) {
        boolean valid = false;
        try {
            Integer.parseInt(value);
            valid = true;
        } catch (Exception e) {
            // ignore
        }
        return valid;
    }

    private static boolean validateDuration(String value) {
        boolean valid = false;
        try {
            Long.parseLong(value);
            valid = true;
        } catch (Exception e) {
            // ignore
        }
        if (!valid) {
            try {
                if (value.startsWith("P") || value.startsWith("-P") || value.startsWith("p") || value.startsWith("-p")) {
                    // its a duration
                    Duration.parse(value);
                } else {
                    // it may be a time pattern, such as 5s for 5 seconds = 5000
                    TimePatternConverter.toMilliSeconds(value);
                }
                valid = true;
            } catch (Exception e) {
                // ignore
            }
        }
        return valid;
    }

    private static String stripOptionalPrefixFromName(Map<String, BaseOptionModel> rows, String name) {
        for (BaseOptionModel row : rows.values()) {
            String optionalPrefix = row.getOptionalPrefix();
            if (optionalPrefix != null && !optionalPrefix.isEmpty() && name.startsWith(optionalPrefix)) {
                // try again
                return stripOptionalPrefixFromName(rows, name.substring(optionalPrefix.length()));
            } else {
                if (name.equalsIgnoreCase(row.getName())) {
                    break;
                }
            }
        }
        return name;
    }

    private static String getPropertyNameFromNameWithPrefix(Map<String, BaseOptionModel> rows, String name) {
        for (BaseOptionModel row : rows.values()) {
            String prefix = row.getPrefix();
            if (prefix != null && !prefix.isEmpty() && name.startsWith(prefix)) {
                return row.getName();
            }
        }
        return null;
    }

    /**
     * Converts the string from dash format into camel case (hello-great-world -> helloGreatWorld)
     *
     * @param  text the string
     * @return      the string camel cased
     */
    private static String dashToCamelCase(String text) {
        if (text == null) {
            return null;
        }
        if (!isDashed(text)) {
            return text;
        }
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '-') {
                i++;
                sb.append(Character.toUpperCase(text.charAt(i)));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

}
