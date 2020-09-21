{%- from 'fluent/settings.sls' import fluent with context %}
{% if fluent.enabled %}
fluent_stop:
  service.dead:
    - enable: False
    {% if fluent.binary == 'td-agent'%}
    - name: td-agent
    {% else %}
    - name: cdp-logging-agent
    {% endif %}
{% endif %}