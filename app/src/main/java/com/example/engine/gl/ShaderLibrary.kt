package com.example.engine.gl

import android.opengl.GLES30
import android.util.Log

object ShaderLibrary {
    private const val TAG = "DraftRigShaders"

    const val MESH_VERTEX_SHADER = """#version 300 es
precision highp float;

layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aTexCoord;
layout(location = 3) in vec4 aJointIndices;
layout(location = 4) in vec4 aJointWeights;
layout(location = 5) in vec3 aColor;

uniform mat4 uModel;
uniform mat4 uView;
uniform mat4 uProjection;
uniform int uUseSkinning;
uniform mat4 uBoneMatrices[32];

out vec3 vWorldPos;
out vec3 vWorldNormal;
out vec2 vTexCoord;
out vec3 vVertexColor;

void main() {
    vec4 localPos = vec4(aPosition, 1.0);
    vec3 localNorm = aNormal;

    if (uUseSkinning == 1) {
        ivec4 jIdx = ivec4(aJointIndices);
        mat4 skinMat =
            aJointWeights.x * uBoneMatrices[clamp(jIdx.x, 0, 31)] +
            aJointWeights.y * uBoneMatrices[clamp(jIdx.y, 0, 31)] +
            aJointWeights.z * uBoneMatrices[clamp(jIdx.z, 0, 31)] +
            aJointWeights.w * uBoneMatrices[clamp(jIdx.w, 0, 31)];
        localPos = skinMat * localPos;
        localNorm = mat3(skinMat) * localNorm;
    }

    vec4 worldPos = uModel * localPos;
    vWorldPos = worldPos.xyz;
    vWorldNormal = normalize(mat3(uModel) * localNorm);
    vTexCoord = aTexCoord;
    vVertexColor = aColor;
    gl_Position = uProjection * uView * worldPos;
}
"""

    const val MESH_FRAGMENT_SHADER = """#version 300 es
precision highp float;

in vec3 vWorldPos;
in vec3 vWorldNormal;
in vec2 vTexCoord;
in vec3 vVertexColor;

uniform vec3 uCameraPos;
uniform vec3 uBaseColor;
uniform float uRoughness;
uniform float uMetallic;
uniform float uOpacity;
uniform vec3 uEmissionColor;
uniform float uEmissionStrength;
uniform float uUvScale;
uniform int uHasTexture;
uniform sampler2D uTexture;
uniform int uShadingMode; // 0 = Solid, 1 = Material, 2 = Wireframe
uniform int uIsSelected;
uniform int uUseVertexColors;

uniform int uLightCount;
uniform vec3 uLightPos[4];
uniform vec3 uLightColor[4];
uniform float uLightIntensity[4];
uniform int uLightType[4]; // 0 = Point, 1 = Directional, 2 = Spot

out vec4 fragColor;

void main() {
    vec3 N = normalize(vWorldNormal);
    if (!gl_FrontFacing) N = -N;
    vec3 V = normalize(uCameraPos - vWorldPos);

    vec3 albedo = (uUseVertexColors == 1) ? vVertexColor : uBaseColor;
    if (uShadingMode == 0) {
        // Blender-style crisp studio clay tint preserving subtle part color cues
        albedo = mix(vec3(0.68, 0.72, 0.80), albedo, 0.42);
    } else if (uHasTexture == 1) {
        vec4 texSample = texture(uTexture, vTexCoord * uUvScale);
        albedo = texSample.rgb * albedo;
    }

    // Hemisphere studio ambient
    float hemi = N.y * 0.5 + 0.5;
    vec3 ambient = mix(vec3(0.08, 0.10, 0.16), vec3(0.24, 0.27, 0.36), hemi) * albedo;

    vec3 lit = ambient;
    float shininess = mix(128.0, 8.0, clamp(uRoughness, 0.05, 1.0));
    vec3 specTint = mix(vec3(0.25), albedo, clamp(uMetallic, 0.0, 1.0));

    int count = clamp(uLightCount, 0, 4);
    if (count == 0) {
        vec3 L = normalize(vec3(0.45, 0.82, 0.55));
        float ndl = max(dot(N, L), 0.0);
        vec3 H = normalize(L + V);
        float spec = pow(max(dot(N, H), 0.0), shininess) * (1.0 - uRoughness * 0.75);
        lit += albedo * ndl * 0.95 + specTint * spec * 0.65;
    } else {
        for (int i = 0; i < count; i++) {
            vec3 L;
            float atten = 1.0;
            if (uLightType[i] == 1) {
                L = normalize(uLightPos[i]);
            } else {
                vec3 toL = uLightPos[i] - vWorldPos;
                float dist = length(toL);
                L = toL / max(dist, 0.001);
                atten = 1.0 / (1.0 + 0.045 * dist + 0.0075 * dist * dist);
                if (uLightType[i] == 2) {
                    vec3 spotDir = normalize(-uLightPos[i]);
                    float cosAng = dot(-L, spotDir);
                    atten *= smoothstep(0.62, 0.90, cosAng);
                }
            }
            float ndl = max(dot(N, L), 0.0);
            vec3 H = normalize(L + V);
            float spec = pow(max(dot(N, H), 0.0), shininess) * (1.0 - uRoughness * 0.7);
            vec3 diffuse = albedo * (1.0 - uMetallic * 0.55) * ndl;
            lit += (diffuse + specTint * spec) * uLightColor[i] * uLightIntensity[i] * atten;
        }
    }

    // Subtle studio rim + emission
    float fresnel = pow(1.0 - max(dot(N, V), 0.0), 3.0);
    lit += uEmissionColor * uEmissionStrength;

    if (uIsSelected == 1) {
        lit += vec3(0.13, 0.83, 0.93) * fresnel * 0.75;
    } else {
        lit += vec3(0.39, 0.40, 0.95) * fresnel * 0.08;
    }

    // Filmic-ish soft tone clamp
    lit = lit / (lit + vec3(0.85)) * 1.45;
    fragColor = vec4(clamp(lit, 0.0, 1.0), clamp(uOpacity, 0.1, 1.0));
}
"""

    const val LINE_VERTEX_SHADER = """#version 300 es
precision highp float;

layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec4 aColor;

uniform mat4 uViewProj;
uniform float uPointSize;

out vec4 vColor;

void main() {
    vColor = aColor;
    gl_Position = uViewProj * vec4(aPosition, 1.0);
    gl_PointSize = uPointSize;
}
"""

    const val LINE_FRAGMENT_SHADER = """#version 300 es
precision highp float;

in vec4 vColor;
out vec4 fragColor;

void main() {
    fragColor = vColor;
}
"""

    fun compileProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSrc)
        val prog = GLES30.glCreateProgram()
        GLES30.glAttachShader(prog, vs)
        GLES30.glAttachShader(prog, fs)
        GLES30.glLinkProgram(prog)
        val status = IntArray(1)
        GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "Program link error: ${GLES30.glGetProgramInfoLog(prog)}")
        }
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "Shader compile error: ${GLES30.glGetShaderInfoLog(shader)}")
        }
        return shader
    }
}
