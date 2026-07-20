package {packageName};

import io.quarkus.qute.TemplateData;
import io.smallrye.config.ConfigMapping;

@TemplateData
@ConfigMapping(prefix = "{configMappingPrefix}.{sectionName}")
public interface {interfaceName} {
{#for property in properties}

    {property.javaType} {property.methodName}();
{/for}
}
