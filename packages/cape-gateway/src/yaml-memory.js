const fs = require('fs');
const path = require('path');

const DEFAULT_SOUL = `# CAPE SOUL

CAPE is a privacy-aware smartphone orchestration agent.

## Operating Rules

- Act only when the user has granted the required Android permission.
- Prefer local deterministic scoring before LLM reasoning.
- Never send raw location trails, full calendars, or usage history to an LLM.
- Explain every automation with a short reason.
- Ask for feedback after high-impact actions.
- If confidence is below \`0.78\`, suggest instead of applying.

## User Respect

The phone should feel calmer, not more controlled. CAPE reduces interruptions and
manual setup burden without hiding what it is doing.
`;

function createYamlMemoryStore(baseDir) {
  const memoryDir = path.resolve(baseDir);
  const profilePath = path.join(memoryDir, 'user_profile.yaml');
  const routinePath = path.join(memoryDir, 'routine_patterns.yaml');
  const soulPath = path.join(path.dirname(memoryDir), 'SOUL.md');

  function ensure() {
    fs.mkdirSync(memoryDir, { recursive: true });
    if (!fs.existsSync(profilePath)) fs.writeFileSync(profilePath, stringifyYaml(defaultProfile()));
    if (!fs.existsSync(routinePath)) fs.writeFileSync(routinePath, stringifyYaml(defaultRoutine()));
    if (!fs.existsSync(soulPath)) fs.writeFileSync(soulPath, DEFAULT_SOUL);
  }

  function readAll() {
    ensure();
    return {
      profile: parseYaml(fs.readFileSync(profilePath, 'utf8')),
      routine: parseYaml(fs.readFileSync(routinePath, 'utf8')),
      soul: fs.readFileSync(soulPath, 'utf8')
    };
  }

  function writeProfile(profile) {
    ensure();
    fs.writeFileSync(profilePath, stringifyYaml(profile));
  }

  function writeRoutine(routine) {
    ensure();
    fs.writeFileSync(routinePath, stringifyYaml(routine));
  }

  function appendSoulRule(rule) {
    ensure();
    const current = fs.readFileSync(soulPath, 'utf8');
    if (current.includes(rule)) return;
    fs.writeFileSync(soulPath, `${current.trimEnd()}\n- ${rule}\n`);
  }

  return {
    ensure,
    readAll,
    writeProfile,
    writeRoutine,
    appendSoulRule,
    paths: {
      profilePath,
      routinePath,
      soulPath
    }
  };
}

function defaultProfile() {
  return {
    user: {
      display_name: 'CAPE Demo User',
      timezone: 'Asia/Calcutta',
      consent: {
        calendar: false,
        location: false,
        usage_stats: false,
        notification_policy_access: false,
        write_settings: false
      }
    },
    preferences: {
      automation_style: 'ask_when_uncertain',
      minimum_confidence_to_apply: 0.78,
      quiet_hours: {
        start: '22:30',
        end: '06:30'
      }
    }
  };
}

function defaultRoutine() {
  return {
    locations: {
      home: { confidence: 0.0, labels: [] },
      office: { confidence: 0.0, labels: [] }
    },
    routines: {
      office_arrival_window: null,
      fatigue_windows: [],
      commute_patterns: []
    },
    pack_feedback: {
      office_focus_high_stress: { accepted: 0, rejected: 0 },
      recovery_mode: { accepted: 0, rejected: 0 },
      commute_alert: { accepted: 0, rejected: 0 }
    },
    overrides: {
      blocked_packs: [],
      blocked_keywords: [],
      conditional_blocks: [],
      notes: []
    }
  };
}

function parseYaml(source) {
  const lines = source.replace(/\r/g, '').split('\n');
  const root = {};
  const stack = [{ indent: -1, value: root }];

  for (let index = 0; index < lines.length; index += 1) {
    const rawLine = lines[index];
    if (!rawLine.trim() || rawLine.trimStart().startsWith('#')) continue;

    const indent = rawLine.match(/^ */)[0].length;
    const trimmed = rawLine.trim();

    while (stack.length > 1 && indent <= stack[stack.length - 1].indent) {
      stack.pop();
    }

    const parent = stack[stack.length - 1].value;

    if (trimmed === '-' || trimmed.startsWith('- ')) {
      if (!Array.isArray(parent)) {
        throw new Error(`Invalid YAML array structure near line ${index + 1}`);
      }
      const itemText = trimmed === '-' ? '' : trimmed.slice(2).trim();
      if (!itemText) {
        const child = {};
        parent.push(child);
        stack.push({ indent, value: child });
      } else if (itemText.includes(': ')) {
        const [key, ...rest] = itemText.split(': ');
        const child = { [key]: parseScalar(rest.join(': ')) };
        parent.push(child);
        stack.push({ indent, value: child });
      } else {
        parent.push(parseScalar(itemText));
      }
      continue;
    }

    const separator = trimmed.indexOf(':');
    if (separator === -1) continue;

    const key = trimmed.slice(0, separator).trim();
    const valueText = trimmed.slice(separator + 1).trim();

    if (valueText === '') {
      const next = nextMeaningfulLine(lines, index + 1);
      const nextTrimmed = next?.trim() ?? '';
      const child = (nextTrimmed === '-' || nextTrimmed.startsWith('- ')) ? [] : {};
      parent[key] = child;
      stack.push({ indent, value: child });
    } else {
      parent[key] = parseScalar(valueText);
    }
  }

  return root;
}

function nextMeaningfulLine(lines, startIndex) {
  for (let index = startIndex; index < lines.length; index += 1) {
    const line = lines[index];
    if (line.trim() && !line.trimStart().startsWith('#')) return line;
  }
  return null;
}

function parseScalar(value) {
  if (value === '[]') return [];
  if (value === '{}') return {};
  if (value === 'null') return null;
  if (value === 'true') return true;
  if (value === 'false') return false;
  if (/^-?\d+(\.\d+)?$/.test(value)) return Number(value);
  if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
    return value.slice(1, -1);
  }
  return value;
}

function stringifyYaml(value, indent = 0) {
  if (Array.isArray(value)) {
    if (value.length === 0) return '[]';
    return value.map(item => {
      if (isPlainObject(item) || Array.isArray(item)) {
        return `${' '.repeat(indent)}-\n${stringifyYaml(item, indent + 2)}`;
      }
      return `${' '.repeat(indent)}- ${formatScalar(item)}`;
    }).join('\n');
  }

  if (!isPlainObject(value)) return formatScalar(value);

  const entries = Object.entries(value);
  return entries.map(([key, child]) => {
    if (Array.isArray(child)) {
      if (child.length === 0) return `${' '.repeat(indent)}${key}: []`;
      return `${' '.repeat(indent)}${key}:\n${stringifyYaml(child, indent + 2)}`;
    }
    if (isPlainObject(child)) {
      return `${' '.repeat(indent)}${key}:\n${stringifyYaml(child, indent + 2)}`;
    }
    return `${' '.repeat(indent)}${key}: ${formatScalar(child)}`;
  }).join('\n') + '\n';
}

function formatScalar(value) {
  if (value === null) return 'null';
  if (typeof value === 'boolean' || typeof value === 'number') return String(value);
  if (value === '') return '""';
  if (/[:#\-\n]/.test(value) || /^\s|\s$/.test(value)) return `"${String(value).replace(/"/g, '\\"')}"`;
  return String(value);
}

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

module.exports = {
  createYamlMemoryStore,
  parseYaml,
  stringifyYaml,
  defaultProfile,
  defaultRoutine
};
