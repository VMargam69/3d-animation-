package com.example.engine.gl

import android.opengl.GLES30
import android.util.Log

object ShaderLibrary {
    private const val TAG = "DraftRigShaders"

    /**
     * Fullscreen procedural 3D Sky Dome gradient shader (zero VBO allocation via gl_VertexID).
     * Renders horizon atmospheric glow, zenith sky tint, subtle sun disc, and grounded lower studio hemisphere.
     */
    const val SKY_VERTEX_SHADER = """#version 300 es
precision highp float;

out vec2 vUv;

void main() {
    vec2 pos[3] = vec2[3](
        vec2(-1.0, -1.0),
        vec2( 3.0, -1.0),
        vec2(-1.0,  3.0)
    );
    vec2 p = pos[gl_VertexID % 3];
    vUv = p * 0.5 + 0.5;
    gl_Position = vec4(p, 0.9999, 1.0);
}
"""

    const val SKY_FRAGMENT_SHADER = """#version 300 es
precision highp float;

in vec2 vUv;

uniform vec3 uZenithColor;
uniform vec3 uHorizonColor;
uniform vec3 uGroundColor;
uniform vec3 uSunColor;
uniform float uHorizonShift;

out vec4 fragColor;

void main() {
    float h = clamp(vUv.y + uHorizonShift, 0.0, 1.0);
    float horizonLine = 0.44;
    vec3 col;
    if (h >= horizonLine) {
        float t = clamp((h - horizonLine) / (1.0 - horizonLine), 0.0, 1.0);
        col = mix(uHorizonColor, uZenithColor, pow(t, 0.68));
    } else {
        float t = clamp((horizonLine - h) / horizonLine, 0.0, 1.0);
        col = mix(uHorizonColor * 0.72, uGroundColor, pow(t, 0.62));
    }
    // Subtle atmospheric horizon band & stylized sun halo
    float band = exp(-pow((h - horizonLine) * 6.5, 2.0));
    col += uSunColor * band * 0.18;
    vec2 sunUv = vec2(0.74, clamp(horizonLine + 0.20, 0.15, 0.90));
    float dSun = length((vUv - sunUv) * vec2(1.4, 1.0));
    float sunGlow = exp(-dSun * 6.5) * 0.34 + smoothstep(0.065, 0.025, dSun) * 0.38;
    col += uSunColor * sunGlow;
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
"""

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
out float vLocalHeight;

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
    vLocalHeight = localPos.y;
    gl_Position = uProjection * uView * worldPos;
}
"""

    const val MESH_FRAGMENT_SHADER = """#version 300 es
precision highp float;

in vec3 vWorldPos;
in vec3 vWorldNormal;
in vec2 vTexCoord;
in vec3 vVertexColor;
in float vLocalHeight;

uniform vec3 uCameraPos;
uniform vec3 uBaseColor;
uniform float uRoughness;
uniform float uMetallic;
uniform float uOpacity;
uniform float uSubsurface;
uniform vec3 uEmissionColor;
uniform float uEmissionStrength;
uniform float uUvScale;
uniform int uHasTexture;
uniform sampler2D uTexture;
uniform int uShadingMode; // 0 = Solid, 1 = Material, 2 = Wireframe, 3 = Toon
uniform int uIsSelected;
uniform int uUseVertexColors;
uniform vec3 uGhostColor;
uniform float uGhostAlpha;

uniform int uLightCount;
uniform vec3 uLightPos[4];
uniform vec3 uLightColor[4];
uniform float uLightIntensity[4];
uniform int uLightType[4]; // 0 = Point, 1 = Directional, 2 = Spot

out vec4 fragColor;

vec3 acesToneMap(vec3 x) {
    const float a = 2.51;
    const float b = 0.03;
    const float c = 2.43;
    const float d = 0.59;
    const float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    vec3 N = normalize(vWorldNormal);
    if (!gl_FrontFacing) N = -N;
    vec3 V = normalize(uCameraPos - vWorldPos);

    // Onion-skinning translucent ghost silhouette pass
    if (uGhostAlpha > 0.001) {
        float rim = pow(1.0 - max(dot(N, V), 0.0), 1.6);
        vec3 ghostCol = mix(uGhostColor * 0.65, uGhostColor * 1.25, rim);
        fragColor = vec4(ghostCol, uGhostAlpha * (0.45 + 0.55 * rim));
        return;
    }

    vec3 albedo = (uUseVertexColors == 1) ? vVertexColor : uBaseColor;
    if (uShadingMode == 0) {
        // Blender-style studio clay matcap preserving subtle character part color separation
        albedo = mix(vec3(0.70, 0.74, 0.82), albedo, 0.38);
    } else if (uHasTexture == 1) {
        vec4 texSample = texture(uTexture, vTexCoord * uUvScale);
        albedo = texSample.rgb * albedo;
    }

    // Subtle ground contact ambient occlusion so characters sit grounded on the stage
    float groundAo = mix(0.76, 1.0, smoothstep(0.0, 0.42, max(vLocalHeight, 0.0)));

    // Pro Toon / Cel Shading mode (uShadingMode == 3)
    if (uShadingMode == 3) {
        vec3 keyDir = (uLightCount > 0) ? normalize(uLightPos[0] - vWorldPos) : normalize(vec3(0.45, 0.82, 0.58));
        float ndl = dot(N, keyDir);
        float band = (ndl > 0.48) ? 1.12 : ((ndl > 0.05) ? 0.84 : 0.46);
        vec3 H = normalize(keyDir + V);
        float toonSpec = smoothstep(0.90, 0.94, max(dot(N, H), 0.0)) * (1.0 - uRoughness * 0.6) * 0.42;
        float ndv = max(dot(N, V), 0.0);
        float ink = smoothstep(0.18, 0.30, ndv);
        vec3 celCol = (albedo * band * groundAo + vec3(toonSpec) + uEmissionColor * uEmissionStrength) * mix(0.16, 1.0, ink);
        if (uIsSelected == 1) {
            float rim = pow(1.0 - ndv, 2.6);
            celCol += vec3(0.13, 0.83, 0.93) * rim * 0.70;
        }
        fragColor = vec4(clamp(celCol, 0.0, 1.0), clamp(uOpacity, 0.1, 1.0));
        return;
    }

    // Studio three-tone hemisphere ambient (sky bounce + warm horizon + deep charcoal floor)
    float hemi = N.y * 0.5 + 0.5;
    vec3 skyBounce = mix(vec3(0.10, 0.12, 0.19), vec3(0.32, 0.36, 0.46), hemi);
    vec3 lit = skyBounce * albedo * groundAo;

    float rough = clamp(uRoughness, 0.06, 1.0);
    float metal = clamp(uMetallic, 0.0, 1.0);
    float sss = clamp(uSubsurface, 0.0, 1.0);
    float shininess = mix(140.0, 10.0, rough);
    vec3 specTint = mix(vec3(0.28), albedo, metal);
    vec3 sssTint = vec3(1.0, 0.44, 0.32);

    int count = clamp(uLightCount, 0, 4);
    if (count == 0) {
        vec3 L = normalize(vec3(0.45, 0.82, 0.58));
        float rawDot = dot(N, L);
        float ndl = max(rawDot, 0.0);
        float wrap = max((rawDot + 0.24 + sss * 0.32) / (1.24 + sss * 0.32), 0.0);
        vec3 H = normalize(L + V);
        float spec = pow(max(dot(N, H), 0.0), shininess) * (1.0 - rough * 0.72);
        vec3 sssGlow = sssTint * pow(max(0.0, 1.0 - abs(rawDot)), 2.0) * sss * 0.24;
        lit += albedo * (mix(ndl, wrap, 0.38) * 0.96 + sssGlow) + specTint * spec * 0.65;
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
                atten = 1.0 / (1.0 + 0.038 * dist + 0.006 * dist * dist);
                if (uLightType[i] == 2) {
                    vec3 spotDir = normalize(-uLightPos[i]);
                    float cosAng = dot(-L, spotDir);
                    atten *= smoothstep(0.62, 0.90, cosAng);
                }
            }
            float rawDot = dot(N, L);
            float ndl = max(rawDot, 0.0);
            float wrap = max((rawDot + 0.20 + sss * 0.34) / (1.20 + sss * 0.34), 0.0);
            vec3 H = normalize(L + V);
            float spec = pow(max(dot(N, H), 0.0), shininess) * (1.0 - rough * 0.68);
            vec3 sssContrib = sssTint * albedo * pow(max(0.0, 1.0 - abs(rawDot)), 2.2) * sss * 0.30;
            vec3 diffuse = albedo * (1.0 - metal * 0.52) * mix(ndl, wrap, 0.32) + sssContrib;
            lit += (diffuse + specTint * spec) * uLightColor[i] * uLightIntensity[i] * atten * groundAo;
        }
    }

    // Crisp studio Fresnel rim + skin backlit warmth + emissive accent
    float fresnel = pow(1.0 - max(dot(N, V), 0.0), 2.8);
    lit += uEmissionColor * uEmissionStrength;
    if (sss > 0.05) {
        lit += sssTint * albedo * fresnel * sss * 0.28;
    }

    if (uIsSelected == 1) {
        lit += vec3(0.13, 0.83, 0.93) * fresnel * 0.68;
    } else {
        lit += vec3(0.39, 0.44, 0.96) * fresnel * 0.12;
    }

    // ACES filmic tone mapping for vivid highlights & clean darks
    vec3 mapped = acesToneMap(lit * 1.12);
    fragColor = vec4(mapped, clamp(uOpacity, 0.1, 1.0));
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
