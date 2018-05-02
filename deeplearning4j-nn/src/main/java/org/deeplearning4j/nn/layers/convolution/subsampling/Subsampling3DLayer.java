/*-
 *
 *  * Copyright 2015 Skymind,Inc.
 *  *
 *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *    you may not use this file except in compliance with the License.
 *  *    You may obtain a copy of the License at
 *  *
 *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *    Unless required by applicable law or agreed to in writing, software
 *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *    See the License for the specific language governing permissions and
 *  *    limitations under the License.
 *
 */

package org.deeplearning4j.nn.layers.convolution.subsampling;

import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.exception.DL4JInvalidInputException;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.conf.ConvolutionMode;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.PoolingType;
import org.deeplearning4j.nn.gradient.DefaultGradient;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.layers.AbstractLayer;
import org.deeplearning4j.nn.workspace.ArrayType;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;
import org.deeplearning4j.util.Convolution3DUtils;
import org.deeplearning4j.util.ConvolutionUtils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.api.ops.Op;
import org.nd4j.linalg.api.ops.impl.layers.convolution.LegacyPooling2D;
import org.nd4j.linalg.api.ops.impl.transforms.IsMax;
import org.nd4j.linalg.api.shape.Shape;
import org.nd4j.linalg.convolution.Convolution;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.nd4j.linalg.primitives.Pair;
import org.nd4j.linalg.util.ArrayUtil;
import org.nd4j.util.OneTimeLogger;

import java.util.Arrays;
import java.util.Properties;


/**
 * Subsampling 3D layer, used for downsampling a 3D convolution
 *
 * @author Max Pumperla
 */
@Slf4j
public class Subsampling3DLayer extends AbstractLayer<org.deeplearning4j.nn.conf.layers.Subsampling3DLayer> {

    protected ConvolutionMode convolutionMode;

    public Subsampling3DLayer(NeuralNetConfiguration conf) {
        super(conf);
        this.convolutionMode =
                ((org.deeplearning4j.nn.conf.layers.Subsampling3DLayer) conf.getLayer()).getConvolutionMode();
    }

    public Subsampling3DLayer(NeuralNetConfiguration conf, INDArray input) {
        super(conf, input);
    }


    @Override
    public double calcL2(boolean backpropParamsOnly) {
        return 0;
    }

    @Override
    public double calcL1(boolean backpropParamsOnly) {
        return 0;
    }

    @Override
    public Type type() {
        return Type.SUBSAMPLING;
    }

    @Override
    public Pair<Gradient, INDArray> backpropGradient(INDArray epsilon, LayerWorkspaceMgr workspaceMgr) {
        assertInputSet(true);

        int miniBatch = input.size(0);
        int inChannels = input.size(1);
        int inD = input.size(2);
        int inH = input.size(3);
        int inW = input.size(4);

        int[] kernel = layerConf().getKernelSize();
        int[] strides = layerConf().getStride();
        int[] dilation = new int[]{1, 1, 1};

        int[] pad;
        int[] outSize;
        if (convolutionMode == ConvolutionMode.Same) {
            outSize = Convolution3DUtils.get3DOutputSize(
                    input, kernel, strides, null, convolutionMode, dilation, true);
            pad = Convolution3DUtils.get3DSameModeTopLeftPadding(
                    outSize, new int[]{inD, inH, inW}, kernel, strides, dilation);
        } else {
            pad = layerConf().getPadding();
            outSize = Convolution3DUtils.get3DOutputSize(
                    input, kernel, strides, pad, convolutionMode, dilation, true);
        }
        int outD = outSize[0];
        int outH = outSize[1];
        int outW = outSize[2];

        int inputHeight = input().size(-2);
        int inputWidth = input().size(-1);
        Gradient retGradient = new DefaultGradient();

        INDArray outEpsilon = null;
        // TODO: call dyn custom op.

        return new Pair<>(retGradient, outEpsilon);
    }


    @Override
    public INDArray activate(boolean training, LayerWorkspaceMgr workspaceMgr) {
        assertInputSet(false);
        if (training && !dropoutApplied && layerConf().getIDropout() != null) {
            applyDropOutIfNecessary(true, workspaceMgr);
        }

        if (input.rank() != 5) {
            throw new DL4JInvalidInputException("Got rank " + input.rank()
                    + " array as input to Subsampling3DLayer with shape " + Arrays.toString(input.shape())
                    + ". Expected rank 5 array with shape [minibatchSize, channels, "
                    + "inputDepth, inputHeight, inputWidth]. "
                    + layerId());
        }

        int miniBatch = input.size(0);
        int inDepth = input.size(1);
        int inD = input.size(2);
        int inH = input.size(3);
        int inW = input.size(4);

        int[] kernel = layerConf().getKernelSize();
        int[] strides = layerConf().getStride();
        int[] dilation = new int[]{1, 1, 1};
        int[] pad;
        int[] outSize;
        if (convolutionMode == ConvolutionMode.Same) {
            outSize = Convolution3DUtils.get3DOutputSize(
                    input, kernel, strides, null, convolutionMode, dilation, true);
            pad = Convolution3DUtils.get3DSameModeTopLeftPadding(
                    outSize, new int[]{inH, inW}, kernel, strides, dilation);
        } else {
            pad = layerConf().getPadding();
            outSize = Convolution3DUtils.get3DOutputSize(
                    input, kernel, strides, pad, convolutionMode, dilation, true);
        }
        int outD = outSize[0];
        int outH = outSize[1];
        int outW = outSize[2];


        INDArray output = workspaceMgr.createUninitialized(ArrayType.ACTIVATIONS,
                new int[]{miniBatch, inDepth, outH, outW}, 'c');

        // TODO: Call dynamic custom op

        return output;
    }

    @Override
    public Layer transpose() {
        throw new UnsupportedOperationException(layerId());
    }

    @Override
    public Layer clone() {
        return new Subsampling3DLayer(conf.clone());
    }

    @Override
    public boolean isPretrainLayer() {
        return false;
    }

    @Override
    public void clearNoiseWeightParams() {
        //no op
    }

    @Override
    public Gradient gradient() {
        throw new UnsupportedOperationException("Not supported - no parameters");
    }

    @Override
    public void fit() {

    }

    @Override
    public int numParams() {
        return 0;
    }

    @Override
    public void fit(INDArray input, LayerWorkspaceMgr workspaceMgr) {
    }

    @Override
    public double score() {
        return 0;
    }

    @Override
    public void accumulateScore(double accum) {
        throw new UnsupportedOperationException(layerId());
    }


    @Override
    public void update(INDArray gradient, String paramType) {

    }

    @Override
    public INDArray params() {
        return null;
    }

    @Override
    public INDArray getParam(String param) {
        return params();
    }

    @Override
    public void setParams(INDArray params) {

    }
}
